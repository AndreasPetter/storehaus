/*
 * Copyright 2014 Twitter, Inc.
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

import org.apache.hadoop.mapred.JobConf
import com.twitter.util.Try
import com.twitter.storehaus.{ ReadableStore, WritableStore }
import com.twitter.storehaus.cascading.versioned.VersionedStorehausCascadingInitializer
import org.apache.hadoop.io.Writable
import com.twitter.storehaus.Store
import scala.reflect.runtime.universe._

/**
 * read and write the name of the object of StorehausCascadingInitializer.
 * Cascading planner seems to be single threaded so we can pass the id 
 * while performing source/sinkConfInit.  
 */
object InitializableStoreObjectSerializer {
  val STORE_TAP_ID = "com.twitter.storehaus.cascading.currenttapid"
  val STORE_CLASS_NAME_READ = "com.twitter.storehaus.cascading.readstoreclass."
  val STORE_CLASS_NAME_WRITE = "com.twitter.storehaus.cascading.writestoreclass."
  val STORE_CLASS_NAME_READ_VERSIONED = "com.twitter.storehaus.cascading.readversionedstoreclass."
  val STORE_CLASS_NAME_WRITE_VERSIONED = "com.twitter.storehaus.cascading.writeversionedstoreclass."
  val STORE_VERSION_READ = "com.twitter.storehaus.cascading.readversion."
  val STORE_VERSION_WRITE = "com.twitter.storehaus.cascading.writeversion."
  
  def setReadableStoreClass[K, V](conf: JobConf, tapid: String, storeSerializer: StorehausCascadingInitializer[K, V]) = {
    if (conf.get(STORE_CLASS_NAME_READ + tapid) == null) conf.set(STORE_CLASS_NAME_READ + tapid, storeSerializer.getClass.getName)
  }
  def setWritableStoreClass[K, V](conf: JobConf, tapid: String, storeSerializer: StorehausCascadingInitializer[K, V]) = {
    if (conf.get(STORE_CLASS_NAME_WRITE + tapid) == null) conf.set(STORE_CLASS_NAME_WRITE + tapid, storeSerializer.getClass.getName)
  }
  def setReadableVersionedStoreClass[K, V](conf: JobConf, tapid: String, storeSerializer: VersionedStorehausCascadingInitializer[K, V]) = {
    if (conf.get(STORE_CLASS_NAME_READ_VERSIONED + tapid) == null) conf.set(STORE_CLASS_NAME_READ_VERSIONED + tapid, storeSerializer.getClass.getName)
  }
  def setWritableVersionedStoreClass[K, V](conf: JobConf, tapid: String, storeSerializer: VersionedStorehausCascadingInitializer[K, V]) = {
    if (conf.get(STORE_CLASS_NAME_WRITE_VERSIONED + tapid) == null) conf.set(STORE_CLASS_NAME_WRITE_VERSIONED + tapid, storeSerializer.getClass.getName)
  }
  def getReadableStore[K, V](conf: JobConf, tapid: String): Try[ReadableStore[K, V]] = {
    Try {
      invokeReflectively[ReadableStore[K, V]]("getReadableStore", conf.get(STORE_CLASS_NAME_READ + tapid), conf).get
    }
  }
  def getWritableStore[K, V](conf: JobConf, tapid: String): Try[WritableStore[K, V]] = {
    Try {
      (invokeReflectively[WritableStore[K, V]]("getWritableStore", conf.get(STORE_CLASS_NAME_WRITE + tapid), conf)).get
    }
  }
  def getReadableStoreIntializer[K, V](conf: JobConf, tapid: String): Try[StorehausCascadingInitializer[K, V]] = {
    Try {
      getReflectiveObject(conf.get(STORE_CLASS_NAME_READ + tapid)).asInstanceOf[StorehausCascadingInitializer[K, V]]
    }
  }
  def getWritableStoreIntializer[K, V](conf: JobConf, tapid: String): Try[StorehausCascadingInitializer[K, V]] = {
    Try {
      getReflectiveObject(conf.get(STORE_CLASS_NAME_WRITE + tapid)).asInstanceOf[StorehausCascadingInitializer[K, V]]
    }
  }
  def getReadableVersionedStoreIntializer[K, V](conf: JobConf, tapid: String, version: Long): Try[VersionedStorehausCascadingInitializer[K, V]] = {
    Try {
      getReflectiveObject(conf.get(STORE_CLASS_NAME_READ_VERSIONED + tapid)).asInstanceOf[VersionedStorehausCascadingInitializer[K, V]]
    }
  }
  def getWritableVersionedStoreIntializer[K, V](conf: JobConf, tapid: String, version: Long): Try[VersionedStorehausCascadingInitializer[K, V]] = {
    Try {
      getReflectiveObject(conf.get(STORE_CLASS_NAME_WRITE_VERSIONED + tapid)).asInstanceOf[VersionedStorehausCascadingInitializer[K, V]]
    }
  }
  def getReadableVersionedStore[K, V](conf: JobConf, tapid: String, version: Long): Try[ReadableStore[K, V]] = {
    Try {
      invokeReflectively("getReadableStore", conf.get(STORE_CLASS_NAME_READ_VERSIONED + tapid), conf, Some(version)).get
    }
  }
  def getWritableVersionedStore[K, V](conf: JobConf, tapid: String, version: Long): Try[WritableStore[K, V]] = {
    Try {
      (invokeReflectively[WritableStore[K, V]]("getWritableStore", conf.get(STORE_CLASS_NAME_WRITE_VERSIONED + tapid), conf, Some(version))).get
    }
  }
  def setTapId(conf: JobConf, tapid: String) = {
    conf.set(STORE_TAP_ID, tapid)
  }
  def getTapId(conf: JobConf): String = {
    conf.get(STORE_TAP_ID)
  }
  def setReadVerion(conf: JobConf, tapid: String, version: Option[Long]) = setVersion(conf, tapid, version, STORE_VERSION_READ)
  def getReadVerion(conf: JobConf, tapid: String): Option[Long] = getVerion(conf, tapid, STORE_VERSION_READ)
  def setWriteVerion(conf: JobConf, tapid: String, version: Option[Long]) = setVersion(conf, tapid, version, STORE_VERSION_WRITE)
  def getWriteVerion(conf: JobConf, tapid: String): Option[Long] = getVerion(conf, tapid, STORE_VERSION_WRITE)
  
  def getReflectiveObject(objectName: String) = {
    val loadermirror = runtimeMirror(getClass.getClassLoader)
	val module = loadermirror.staticModule(objectName)
	loadermirror.reflectModule(module).instance    
  }
  
  def invokeReflectively[T](methodName: String, objectName: String, conf: JobConf, version: Option[Long] = None): Option[T] = {
	// If i use scala reflection, i always get a feeling of misunderstanding the whole universe 
	val loadermirror = runtimeMirror(getClass.getClassLoader)
    val instancemirror = loadermirror.reflect(getReflectiveObject(objectName))
	val method = instancemirror.symbol.typeSignature.member(newTermName(methodName)).asMethod
	version match {
	  case None => instancemirror.reflectMethod(method)(conf).asInstanceOf[Option[T]]
	  case Some(version) => instancemirror.reflectMethod(method)(conf, version).asInstanceOf[Option[T]]
	}
  }

  private def setVersion(conf: JobConf, tapid: String, version: Option[Long], cfid: String) =
    version.map(v => conf.set(cfid + tapid, v.toString))
  private def getVerion(conf: JobConf, tapid: String, cfid: String): Option[Long] = {
    conf.get(cfid + tapid) match {
      case null => None
      case "" => None
      case x => Some(x.toLong)  
    }
  }
}
