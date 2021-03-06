/*
 * Copyright 2014 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twitter.storehaus.cascading

import java.io.IOException
import org.apache.hadoop.mapred.{ OutputFormat, JobConf, RecordWriter, Reporter }
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.util.Progressable
import org.slf4j.{ Logger, LoggerFactory }
import com.twitter.storehaus.WritableStore
import com.twitter.util.{ Await, Try }
import scala.reflect.runtime._
import scala.collection.mutable.HashMap

/**
 * StorehausOuputFormat using a WriteableStore
 */
class StorehausOutputFormat[K, V] extends OutputFormat[K, V] {
  import StorehausOutputFormat.{ FORCE_FUTURE_IN_OUTPUTFORMAT, FORCE_FUTURE_BATCH_IN_OUTPUTFORMAT }

  @transient private val log = LoggerFactory.getLogger(classOf[StorehausOutputFormat[K, V]])

  /**
   * Simple StorehausRecordWriter delegating method-calls to store
   */
  class StorehausRecordWriter(val conf: JobConf, val progress: Progressable) extends RecordWriter[K, V] {
    val throttler = StorehausOutputFormat.getThrottlerClass(conf)
    val buffer = new HashMap[K, Option[V]]
    val maxbuffersize = Try(conf.get(FORCE_FUTURE_BATCH_IN_OUTPUTFORMAT).toInt).getOrElse(0)
    val parallel = conf.get(FORCE_FUTURE_IN_OUTPUTFORMAT) != null && conf.get(FORCE_FUTURE_IN_OUTPUTFORMAT).equalsIgnoreCase("true")
    val tapid = InitializableStoreObjectSerializer.getTapId(conf)
    log.info(s"Will write tuples in ${if(parallel) "parallel" else "sequence"}")
    log.info(s"Throttler is $throttler")
    var store: Option[WritableStore[K, Option[V]]] = None
    override def write(key: K, value: V): Unit = {
      store = if (store.isEmpty) {
        log.info(s"RecordWriter initializes the store.")
        (InitializableStoreObjectSerializer.getWriteVerion(conf, tapid) match {
          case None          => InitializableStoreObjectSerializer.getWritableStore[K, Option[V]](conf, tapid)
          case Some(version) => InitializableStoreObjectSerializer.getWritableVersionedStore[K, Option[V]](conf, tapid, version)
        }).onSuccess { store =>
          log.info(s"Configuring $throttler")
          throttler.map(_.configure(conf, store))
        }
      }.onFailure(e => log.error(s"RecordWriter was not able to initialize the store for tap $tapid.", e)).toOption
      else store
      throttler.map(_.throttle)
      // handle with care - make sure thread pools shut down TPEs on used stores correctly if asynchronous
      // that includes awaitTermination and adding shutdown hooks, depending on mode of operation of Hadoop
      if (parallel) {
        if (maxbuffersize > 1) {
          // be aware that in case of an unorderly shutdown of this vm we might loose the rest of the buffer will not be persisted
          if (buffer.size == maxbuffersize) {
            store.map(_.multiPut(buffer.toMap))
            buffer.clear()
          } else {
            buffer.put(key, Some(value))
          }
        } else {
          store.get.put((key, Some(value))).onFailure { case e: Exception => throw new IOException(e) }
        }
      } else {
        Try(Await.result(store.get.put((key, Some(value))))).onFailure { throwable => new IOException(throwable) }
      }
    }
    override def close(reporter: Reporter): Unit = {
      log.info(s"RecordWriter finished. Closing.")
      if (buffer.size > 0) {
        store.map(_.multiPut(buffer.toMap))
      }
      throttler.map(_.close)
      store.map(_.close())
      reporter.setStatus("Completed Writing. Closed Store.")
    }
  }

  /**
   * initializes a WritableStore out of serialized JobConf parameters and returns a RecordWriter
   * putting into that store.
   */
  override def getRecordWriter(fs: FileSystem, conf: JobConf, name: String, progress: Progressable): RecordWriter[K, V] = {
    StorehausInputFormat.getResourceConfClass(conf).get.configure(conf)
    new StorehausRecordWriter(conf, progress)
  }

  override def checkOutputSpecs(fs: FileSystem, conf: JobConf) = {}
}

object StorehausOutputFormat {

  val FORCE_FUTURE_IN_OUTPUTFORMAT = "com.twitter.storehaus.cascading.outputformat.forcefuture"
  val FORCE_FUTURE_BATCH_IN_OUTPUTFORMAT = "com.twitter.storehaus.cascading.outputformat.forcefuturebatch"
  val OUTPUT_THROTTLER_CLASSNAME_CONFID = "com.twitter.storehaus.cascading.splitting.outputthrottler.class"

  def setThrottlerClass[T <: OutputThrottler](conf: JobConf, resourceConf: Class[T]) = {
    conf.set(OUTPUT_THROTTLER_CLASSNAME_CONFID, resourceConf.getName)
  }

  def getThrottlerClass(conf: JobConf): Try[OutputThrottler] =
    StorehausInputFormat.getConfClass[OutputThrottler](conf, OUTPUT_THROTTLER_CLASSNAME_CONFID, () => NullThrottler)

  /**
   * used to initialize map-side resources
   */
  trait OutputThrottler {
    def configure[K, V](conf: JobConf, store: WritableStore[K, Option[V]])
    def throttle
    def close
  }

  object NullThrottler extends OutputThrottler {
    override def configure[K, V](conf: JobConf, store: WritableStore[K, Option[V]]) = {}
    override def throttle = {}
    override def close = {}
  }
}