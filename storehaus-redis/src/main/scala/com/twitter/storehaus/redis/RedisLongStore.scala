/*
 * Copyright 2013 Twitter Inc.
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

package com.twitter.storehaus.redis

import com.twitter.algebird.Monoid
import com.twitter.bijection.Injection
import com.twitter.finagle.redis.Client
import com.twitter.finagle.redis.util.{ CBToString, StringToChannelBuffer }
import com.twitter.storehaus.algebra.{ ConvertedStore, MergeableStore }
import com.twitter.util.Time
import org.jboss.netty.buffer.{ ChannelBuffer, ChannelBuffers }
import scala.util.control.Exception.allCatch

/**
 * 
 * @author Doug Tangren
 */

object RedisLongStore {
   /** redis stores numerics as strings
    *  so we have to encode/decode them as such
    *  http://redis.io/topics/data-types-intro
    */
  implicit object LongInjection
   extends Injection[Long, ChannelBuffer] {
    def apply(a: Long): ChannelBuffer =
      StringToChannelBuffer(a.toString)
    override def invert(b: ChannelBuffer): Option[Long] =
      allCatch.opt(CBToString(b).toLong)
  }
  def apply(client: Client, ttl: Option[Time] = RedisStore.Default.TTL) =
    new RedisLongStore(RedisStore(client, ttl))
}
import RedisLongStore._

/**
 * A MergableStore backed by redis which stores Long values.
 * Values are merged with an incrBy operation.
 */
class RedisLongStore(underlying: RedisStore)
  extends ConvertedStore[ChannelBuffer, ChannelBuffer, ChannelBuffer, Long](underlying)(identity)
     with MergeableStore[ChannelBuffer, Long] {
  val monoid = implicitly[Monoid[Long]]
  override def merge(kv: (ChannelBuffer, Long)) = underlying.client.incrBy(kv._1, kv._2).unit
}
