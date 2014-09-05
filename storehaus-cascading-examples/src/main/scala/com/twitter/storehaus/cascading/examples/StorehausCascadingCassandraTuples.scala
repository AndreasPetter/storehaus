package com.twitter.storehaus.cascading.examples

import cascading.flow.FlowDef
import cascading.flow.hadoop.HadoopFlowConnector
import cascading.pipe.Pipe
import cascading.pipe.Each
import cascading.property.AppProps
import cascading.scheme.hadoop.TextDelimited
import cascading.tap.Tap
import cascading.tap.hadoop.Hfs
import java.util.Properties
import org.apache.hadoop.mapred.JobConf
import com.twitter.algebird.Semigroup
import com.twitter.storehaus.cassandra.cql.{ AbstractCQLCassandraCompositeStore, CassandraTupleStore, CQLCassandraCompositeStore }
import com.twitter.storehaus.cassandra.cql.cascading.{ CassandraCascadingInitializer, CassandraCascadingRowMatcher, CassandraSplittingMechanism }
import com.twitter.storehaus.{ReadableStore, WritableStore}
import com.twitter.storehaus.cascading.{ StorehausCascadingInitializer, StorehausTap }
import com.twitter.storehaus.cascading.StorehausInputFormat
import scala.language.implicitConversions
import shapeless._
import HList._
import Traversables._
import Nat._
import UnaryTCConstraint._
import shapeless.Tuples._
import java.util.Date
import com.websudos.phantom.CassandraPrimitive
import scala.reflect.runtime.universe._
import scala.reflect._
import cascading.pipe.Each


/**
 * shows how to use CassandraTupleStore with Cascading
 * See the Tuple1 type parameter. This is because Scala is strange 
 * concerning handling of Tuple1, so one explicitly will provide it
 * only if it's a "single"-Tuple ...
 */
object StoreInitializerTuples
    extends StorehausCascadingInitializer[((String, Int),Tuple1[Long]), String] 
    with CassandraCascadingInitializer[((String, Int),Tuple1[Long]), String] {
  // this example StoreInitializer is for a storehaus-cassandra store
  // for storehaus-cassandra type parameters are HLists
  // setup basic store parameters (special to storehaus-cassandra, setup is different for other stores)
  import com.twitter.storehaus.cassandra.cql.CQLCassandraConfiguration._
 
  override def getColumnFamilyName = "ColumnFamilyNameForTuples"
  override def getKeyspaceName = "mytestkeyspace"
  override def getThriftConnections = "192.168.3.2:9160"
    
  val columnFamily = StoreColumnFamily(getColumnFamilyName, 
      StoreSession(getKeyspaceName, StoreCluster("Test Cluster", Set(StoreHost("192.168.3.2")))))
  
  
  // setup key serializers for HLists by example (special to storehaus-cassandra, setup is different for other stores)
  val rs = AbstractCQLCassandraCompositeStore.getSerializerHListByExample("Bla und Blubb" :: 1234 :: HNil)
  val cs = AbstractCQLCassandraCompositeStore.getSerializerHListByExample(12345l :: HNil)
  
  // setup column names (special to storehaus-cassandra, setup is different for other stores) and a type of values
  val rowKeyNames = List("name", "somenumber")
  val colKeyNames = List("otherlongnumber")
  val valueSerializer = implicitly[CassandraPrimitive[String]]
  
  // create store
  val cassandrastore = new CQLCassandraCompositeStore(columnFamily, rs, rowKeyNames, cs, colKeyNames)(valueSerializer) 
  // last param avoid providing complex HList 
  val store = new CassandraTupleStore(cassandrastore, (("", 0), Tuple1(2l))) 
  
  /**
   * store prepare, do this similar for other storehaus-stores
   */
  override def prepareStore: Boolean = {
    // creates keyspace and columnfamily
    CQLCassandraCompositeStore.createColumnFamily(columnFamily, rs, rowKeyNames, cs, colKeyNames, valueSerializer)
    true
  }
  
  /**
   * used to intialize the store, do this similar for other storehaus-stores
   */
  override def getReadableStore(jobconf: JobConf): Option[ReadableStore[((String, Int),Tuple1[Long]), String]] = {    
    // automatic type conversion doesn't seem to work here
    Some(store.asInstanceOf[ReadableStore[((String, Int),Tuple1[Long]), String]])
  }
  
  /**
   * used to intialize the store, do this similar for other storehaus-stores
   */
  override def getWritableStore(jobconf: JobConf): Option[WritableStore[((String, Int),Tuple1[Long]), Option[String]]] = {    
    // automatic type conversion doesn't seem to work here
    Some(store.asInstanceOf[WritableStore[((String, Int),Tuple1[Long]), Option[String]]])
  }
  
  // Cassandra specific method used to transform rows from the cassandra hadoop input format into keys and values
  override def getCascadingRowMatcher = store.asInstanceOf[CassandraCascadingRowMatcher[((String, Int),Tuple1[Long]), String]]
}

/**
 * uses a different column Family to copy the data over
 */
object StoreInitializerTuplesWriter
    extends StorehausCascadingInitializer[((String, Int),Tuple1[Long]), String] 
    with CassandraCascadingInitializer[((String, Int),Tuple1[Long]), String] {
  import com.twitter.storehaus.cassandra.cql.CQLCassandraConfiguration._
  import StoreInitializerTuples._
  override def getColumnFamilyName = "ColumnFamilyNameForTuplesOutput"
  override def getKeyspaceName = StoreInitializerTuples.getKeyspaceName
  override def getThriftConnections = StoreInitializerTuples.getThriftConnections
  val columnFamily = StoreColumnFamily(getColumnFamilyName, 
      StoreSession(getKeyspaceName, StoreCluster("Test Cluster", Set(StoreHost("192.168.3.2")))))  
  val cassandrastore = new CQLCassandraCompositeStore(columnFamily, StoreInitializerTuples.rs, StoreInitializerTuples.rowKeyNames, StoreInitializerTuples.cs, StoreInitializerTuples.colKeyNames)(StoreInitializerTuples.valueSerializer) 
  val store = new CassandraTupleStore(cassandrastore, (("", 0), Tuple1(2l))) 
  override def prepareStore: Boolean = {
    CQLCassandraCompositeStore.createColumnFamily(columnFamily, rs, rowKeyNames, cs, colKeyNames, valueSerializer)
    true
  }
  override def getReadableStore(jobconf: JobConf): Option[ReadableStore[((String, Int),Tuple1[Long]), String]] = {    
    Some(store.asInstanceOf[ReadableStore[((String, Int),Tuple1[Long]), String]])
  }
  override def getWritableStore(jobconf: JobConf): Option[WritableStore[((String, Int),Tuple1[Long]), Option[String]]] = {    
    Some(store.asInstanceOf[WritableStore[((String, Int),Tuple1[Long]), Option[String]]])
  }
  override def getCascadingRowMatcher = store.asInstanceOf[CassandraCascadingRowMatcher[((String, Int),Tuple1[Long]), String]]
}

object StorehausCascadingCassandraTuplesExample {
  
  /**
   * used to add some data for testing; in a real setup data is probably 
   * already existing, so this can be skipped
   */
  def putSomeDataForFun() = {
    StoreInitializerTuplesWriter.prepareStore
    StoreInitializerTuples.prepareStore
    val store = StoreInitializerTuples.getWritableStore(null).get
    for(i <- 0 until 10000) {
    	store.put(((((i * 5644).toString, i),Tuple1(i)), Some(i.toString)))
    }
  }
  
  /**
   * see http://docs.cascading.org/impatient/impatient1.html
   * changed in- and output taps
   */
  def main(args: Array[String]): Unit = {
    putSomeDataForFun
    
    val conf = new JobConf
    // use different splitting mechanism; optimized for Cassandra (but could also use a generic one like JobConfKeyArraySplittingMechanism)
    StorehausInputFormat.setSplittingClass[((String, Int),Tuple1[Long]), String, CassandraSplittingMechanism[((String, Int),Tuple1[Long]), String]](conf, classOf[CassandraSplittingMechanism[((String, Int),Tuple1[Long]), String]])
    val properties = AppProps.appProps()
      .setName("StorehausCascadingCassandraTuplesExample")
      .setVersion("0.0.1-SNAPSHOT")
      .buildProperties(conf)
    AppProps.setApplicationJarClass( properties, StorehausCascadingStandaloneTest.getClass )
    
    val flowConnector = new HadoopFlowConnector( properties )
    // create the source tap
    val inTap = new StorehausTap[((String, Int),Tuple1[Long]), String](StoreInitializerTuples)

    // create the sink tap
    val outTap = new StorehausTap[((String, Int),Tuple1[Long]), String](StoreInitializerTuplesWriter)

    // specify a pipe to connect the taps
    val copyPipe = new Pipe( "copy" )
    
    // connect the taps, pipes, etc., into a flow
    val flowDef = FlowDef.flowDef()
     .addSource( copyPipe, inTap.asInstanceOf[cascading.tap.Tap[_,_,_]] )
     .addTailSink( copyPipe, outTap.asInstanceOf[cascading.tap.Tap[_,_,_]] )

    flowDef.setAssertionLevel(cascading.operation.AssertionLevel.STRICT)
    flowDef.setDebugLevel(cascading.operation.DebugLevel.VERBOSE)
    // run the flow
    val x = flowConnector.connect( flowDef )
    val y = x.complete()
  }
}