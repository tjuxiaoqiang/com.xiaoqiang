package com.quantifind.kafka

import scala.collection._
import com.quantifind.kafka.OffsetGetter.{BrokerInfo, KafkaInfo, OffsetInfo}
import kafka.common.{NoBrokersForPartitionException}
import com.quantifind.kafka.SimpleConsumer
import kafka.cluster.Broker
import kafka.utils.{ZkUtils}
import org.I0Itec.zkclient.ZkClient
import org.I0Itec.zkclient.exception.ZkNoNodeException
import com.twitter.util.Time
import org.apache.zookeeper.data.Stat
import com.quantifind.utils.Json
import com.quantifind.utils.Logging
import com.quantifind.utils._
import kafka.consumer.TopicCount
import kafka.consumer.StaticTopicCount
import scala.reflect.ClassManifestDeprecatedApis
import com.quantifind.kafka.SimpleConsumer
import com.xiaonei.kafka_proxy.KafkaConsumerProxyClient
import util.parsing.json.JSON

/**
 * a nicer version of kafka's ConsumerOffsetChecker tool
 * User: pierre
 * Date: 1/22/14
 */

case class Node(name: String, children: Seq[Node] = Seq())
case class TopicDetails(consumers: Seq[ConsumerDetail])
case class ConsumerDetail(name: String)

class OffsetGetter(zkClient: ZkClient) extends Logging {

  private val consumerMap: mutable.Map[Int, Option[SimpleConsumer]] = mutable.Map()

  private def getConsumer(bid: Int): Option[SimpleConsumer] = {
    try {
      readDataMaybeNull(zkClient, ZkUtils.BrokerIdsPath + "/" + bid) match {
        case (Some(brokerInfoString), _) =>
          val pieces = brokerInfoString.split(":")
          val host = pieces(1)
          val port = pieces(2).toInt
          val consumer = new SimpleConsumer(host, port, 10000, 100000)
          Some(consumer)
        case (None, _) =>
          throw new NoBrokersForPartitionException("Broker id %d does not exist".format(bid))
      }
    } catch {
      case t: Throwable =>
        error("Could not parse broker info", t)
        throw t
        None
    }
  }
  
  private def getBroker(pid: String): Option[Int] = {
  	  if (pid == null)
  	  	None
  	  try {
  	      val pieces = pid.split("-")
  	      Some(pieces(0).toInt)
  	  } catch {
  	  	case t: Throwable =>
      	  	error("pid is wrong. pid=" + pid)
      	  	None
  	  }
  	  
  }

  private def processPartition(group: String, topic: String, pid: String): Option[OffsetInfo] = {
    try {
      val (offset, stat: Stat) = readData(zkClient, s"${ZkUtils.ConsumersPath}/$group/offsets/$topic/$pid")
      val (owner, _) = readDataMaybeNull(zkClient, s"${ZkUtils.ConsumersPath}/$group/owners/$topic/$pid")

      getBroker(pid) match {
        case Some(bid) =>
          val consumerOpt = consumerMap.getOrElseUpdate(bid, getConsumer(bid))
          consumerOpt map {
            consumer =>
//              val topicAndPartition = TopicAndPartition(topic, pid)
//              val request =
//                OffsetRequest(immutable.Map(topicAndPartition -> PartitionOffsetRequestInfo(OffsetRequest.LatestTime, 1)))
//              val logSize = consumer.getOffsetsBefore(request).partitionErrorAndOffsets(topicAndPartition).offsets.head
            	val pieces = pid.split("-")
            	val logSize = KafkaConsumerProxyClient.getOffsetsBefore(topic, pieces(1).toInt, -1L, 1, consumer.host, consumer.port).get(0)

              OffsetInfo(group = group,
                topic = topic,
                partition = pid,
                offset = offset.toLong,
                logSize = logSize,
                owner = owner,
                creation = Time.fromMilliseconds(stat.getCtime),
                modified = Time.fromMilliseconds(stat.getMtime))
          }
        case None =>
          error("No broker for partition %s - %s".format(topic, pid))
          None
      }
    } catch {
      case t: Throwable =>
        error(s"Could not parse partition info. group: [$group] topic: [$topic]", t)
        throw t
        None
    }
  }
  
  private def getPartitionsForTopics(zkClient: ZkClient, topics: Iterator[String]): mutable.Map[String, List[String]] = {
    var ret = new mutable.HashMap[String, List[String]]()
    for (topic <- topics) {
      var partList: List[String] = Nil
      val brokers = getChildrenParentMayNotExist(zkClient, ZkUtils.BrokerTopicsPath + "/" + topic)
      for (broker <- brokers) {
        val nParts = readData(zkClient, ZkUtils.BrokerTopicsPath + "/" + topic + "/" + broker)._1.toInt
        for (part <- 0 until nParts)
          partList ::= broker + "-" + part
      }
      partList = partList.sortWith((s,t) => s < t)
      ret += (topic -> partList)
    }
    ret
  }

  private def processTopic(group: String, topic: String): Seq[OffsetInfo] = {
    val pidMap = getPartitionsForTopics(zkClient, Seq(topic).iterator)
    for {
      partitions <- pidMap.get(topic).toSeq
      pid <- partitions.sorted
      info <- processPartition(group, topic, pid)
    } yield info
  }

  private def brokerInfo(): Iterable[BrokerInfo] = {
    for {
      (bid, consumerOpt) <- consumerMap
      consumer <- consumerOpt
    } yield BrokerInfo(id = bid, host = consumer.host, port = consumer.port)
  }

  private def offsetInfo(group: String, topics: Seq[String] = Seq()): Seq[OffsetInfo] = {
    val topicList = if (topics.isEmpty) {
      try {
        getChildren(
          zkClient, s"${ZkUtils.ConsumersPath}/$group/offsets").toSeq
      } catch {
        case _: ZkNoNodeException => Seq()
      }
    } else {
      topics
    }
    topicList.sorted.flatMap(processTopic(group, _))
  }

  def getInfo(group: String, topics: Seq[String] = Seq()): KafkaInfo = {
    val off = offsetInfo(group, topics)
    val brok = brokerInfo()
    KafkaInfo(
      brokers = brok.toSeq,
      offsets = off
    )
  }
  
  private def getChildren(client: ZkClient, path: String): Seq[String] = {
    import scala.collection.JavaConversions._
    // triggers implicit conversion from java list to scala Seq
    client.getChildren(path)
  }

  def getGroups: Seq[String] = {
    getChildren(zkClient, ZkUtils.ConsumersPath)
  }


  /**
   * returns details for a given topic such as the active consumers pulling off of it
   * @param topic
   * @return
   */
  def getTopicDetail(topic: String): TopicDetails = {
    val topicMap = getActiveTopicMap

    if(topicMap.contains(topic)) {
      TopicDetails(topicMap(topic).map(consumer => {
        ConsumerDetail(consumer.toString)
      }).toSeq)
    } else {
      TopicDetails(Seq(ConsumerDetail("Unable to find Active Consumers")))
    }
  }

  def getTopics: Seq[String] = {
    getChildren(zkClient, ZkUtils.BrokerTopicsPath).sortWith(_ < _)
  }
  
  private def constructTopicCount(group: String,
                          consumerId: String,
                          zkClient: ZkClient) : TopicCount = {
    val dirs = new ZKGroupDirs(group)
    val topicCountString = ZkUtils.readData(zkClient, dirs.consumerRegistryDir + "/" + consumerId)

      var topMap : Map[String,Int] = null
      try {
        SyncJSON.parseFull(topicCountString) match {
          case Some(m) => topMap = m.asInstanceOf[Map[String,Int]]
          case None => throw new RuntimeException("error constructing TopicCount : " + topicCountString)
        }
      }
      catch {
        case e: Throwable =>
          error("error parsing consumer json string " + topicCountString, e)
          throw e
      }

      new StaticTopicCount(consumerId, topMap)
  }
  
  private def getConsumersPerTopic(zkClient: ZkClient, group: String) : mutable.Map[String, List[String]] = {
    val dirs = new ZKGroupDirs(group)
    val consumers = getChildrenParentMayNotExist(zkClient, dirs.consumerRegistryDir)
    val consumersPerTopicMap = new mutable.HashMap[String, List[String]]
    for (consumer <- consumers) {
      val topicCount = TopicCount.constructTopicCount(group, consumer, zkClient)
      for ((topic, consumerThreadIdSet) <- topicCount.getConsumerThreadIdsPerTopic) {
        for (consumerThreadId <- consumerThreadIdSet)
          consumersPerTopicMap.get(topic) match {
            case Some(curConsumers) => consumersPerTopicMap.put(topic, consumerThreadId :: curConsumers)
            case _ => consumersPerTopicMap.put(topic, List(consumerThreadId))
          }
      }
    }
    for ( (topic, consumerList) <- consumersPerTopicMap )
      consumersPerTopicMap.put(topic, consumerList.sortWith((s,t) => s < t))
    consumersPerTopicMap
  }


  /**
   * returns a map of active topics-> list of consumers from zookeeper, ones that have IDS attached to them
   *
   * @return
   */
  def getActiveTopicMap: Map[String, Seq[String]] = {
    val topicMap: mutable.Map[String, Seq[String]] = mutable.Map()

    getChildren(zkClient, ZkUtils.ConsumersPath).foreach(group => {
      getConsumersPerTopic(zkClient, group).keySet.foreach(key => {
        if (!topicMap.contains(key)) {
          topicMap.put(key, Seq(group))
        } else {
          topicMap.put(key, topicMap(key) :+ group)
        }
      })
    })
    topicMap.toMap
  }

  def getActiveTopics: Node = {
    val topicMap = getActiveTopicMap

    Node("ActiveTopics", topicMap.map {
      case (s: String, ss: Seq[String]) => {
        Node(s, ss.map(consumer => Node(consumer)))

      }
    }.toSeq)
  }
  
  private def getChildrenParentMayNotExist(client: ZkClient, path: String): Seq[String] = {
    import scala.collection.JavaConversions._
    // triggers implicit conversion from java list to scala Seq
    try {
      client.getChildren(path)
    } catch {
      case e: ZkNoNodeException => return Nil
      case e2: Throwable => throw e2
    }
  }

  def getClusterViz: Node = {
  	val brokerIds = getChildrenParentMayNotExist(zkClient, ZkUtils.BrokerIdsPath).sorted
  	
  	val clusterNodes = brokerIds.map(_.toInt).map(ZkUtils.getCluster(zkClient).getBroker(_)).filter(_.isDefined).map(_.get).map((broker) => {
  		val zkString = broker.getZKString();
  		val pieces = zkString.split(":")
        Node(pieces(1) + ":" + pieces(2), Seq())
    })
  	
  	
//    val clusterNodes = ZkUtils.getAllBrokersInCluster(zkClient).map((broker) => {
//        Node(broker.getConnectionString(), Seq())
//    })
    Node("KafkaCluster", clusterNodes)
  }
  
//  def getBrokerInfo(zkClient: ZkClient, brokerId: Int): Option[Broker] = {
//    readDataMaybeNull(zkClient, ZkUtils.BrokerIdsPath + "/" + brokerId)._1 match {
//      case Some(brokerInfo) => Some(Broker.createBroker(brokerId, brokerInfo))
//      case None => None
//    }
//  }

  def close() {
    for (consumerOpt <- consumerMap.values) {
      consumerOpt match {
        case Some(consumer) => consumer.close()
        case None => // ignore
      }
    }
  }
  
  def readData(client: ZkClient, path: String): (String, Stat) = {
    val stat: Stat = new Stat()
    val dataStr: String = client.readData(path, stat)
    (dataStr, stat)
  }

  def readDataMaybeNull(client: ZkClient, path: String): (Option[String], Stat) = {
    val stat: Stat = new Stat()
    val dataAndStat = try {
                        (Some(client.readData(path, stat)), stat)
                      } catch {
                        case e: ZkNoNodeException =>
                          (None, stat)
                        case e2: Throwable => throw e2
                      }
    dataAndStat
  }

}

object OffsetGetter {

  case class KafkaInfo(brokers: Seq[BrokerInfo], offsets: Seq[OffsetInfo])

  case class BrokerInfo(id: Int, host: String, port: Int)

  case class OffsetInfo(group: String,
                        topic: String,
                        partition: String,
                        offset: Long,
                        logSize: Long,
                        owner: Option[String],
                        creation: Time,
                        modified: Time) {
    val lag = logSize - offset
  }

}


class ZKGroupDirs(val group: String) {
  def consumerDir = ZkUtils.ConsumersPath
  def consumerGroupDir = consumerDir + "/" + group
  def consumerRegistryDir = consumerGroupDir + "/ids"
}

object SyncJSON extends Logging {
  val myConversionFunc = {input : String => input.toInt}
  JSON.globalNumberParser = myConversionFunc
  val lock = new Object

  def parseFull(input: String): Option[Any] = {
    lock synchronized {
      try {
        JSON.parseFull(input)
      } catch {
        case t: Throwable =>
          throw new RuntimeException("Can't parse json string: " + input, t)
      }
    }
  }
}