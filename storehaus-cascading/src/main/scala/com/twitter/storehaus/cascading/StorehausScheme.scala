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

import com.twitter.storehaus.{ ReadableStore, WritableStore }
import java.util.UUID
import cascading.flow.FlowProcess
import cascading.scheme.{ Scheme, SinkCall, SourceCall }
import cascading.tuple.Fields
import org.apache.hadoop.mapred.{ JobConf, OutputCollector, RecordReader }

/**
 * Scheme can be instantiated on stores conforming to Readable- or WritableStore or both
 */
class StorehausScheme[K, V]
  (@transient store: StorehausCascadingInitializer[K, V], id: String = UUID.randomUUID.toString)
  extends Scheme[JobConf, RecordReader[Instance[K], Instance[V]], OutputCollector[K, V], Seq[Object], Seq[Object]](new Fields("key", "value")) 
  with StorehausSchemeBasics[K, V] {

  def getId = this.id
 
  override def setReadStore(conf: JobConf) = {
    InitializableStoreObjectSerializer.setReadableStoreClass(conf, getId, store)
    InitializableStoreObjectSerializer.setReadVerion(conf, getId, None)
  }

  override def setWriteStore(conf: JobConf) = {
    InitializableStoreObjectSerializer.setWritableStoreClass(conf, getId, store)
    InitializableStoreObjectSerializer.setWriteVerion(conf, getId, None)
  }

  override def sourceCleanup(process: FlowProcess[JobConf], sourceCall: SourceCall[Seq[Object], 
    RecordReader[Instance[K], Instance[V]]]): Unit = super[StorehausSchemeBasics].sourceCleanup(process, sourceCall)
  
  override def sourcePrepare(process: FlowProcess[JobConf], sourceCall : SourceCall[Seq[Object], 
    RecordReader[Instance[K], Instance[V]]]) : Unit = super[StorehausSchemeBasics].sourcePrepare(process, sourceCall)
    
  override def equals(o : Any) : Boolean =
    o.getClass.equals(this.getClass) && id.equals(o.asInstanceOf[StorehausScheme[K, V]].getId)
}
