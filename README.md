Kafka Offset Monitor
===========

[![Build Status](https://travis-ci.org/quantifind/KafkaOffsetMonitor.svg?branch=master)](https://travis-ci.org/quantifind/KafkaOffsetMonitor)

This is an app to monitor your kafka consumers and their position (offset) in the queue.

You can see the current consumer groups, for each group the topics that they are consuming and the position of the group in each topic queue. This is useful to understand how quick you are consuming from a queue and how fast the queue is growing. It allows for debuging kafka producers and consumers or just to have an idea of what is going on in  your system.

The app keeps an history of queue position and lag of the consumers so you can have an overview of what has happened in the last days.

Here are a few screenshots:

List of Consumer Groups
-----------------------

![Consumer Groups](http://quantifind.github.io/KafkaOffsetMonitor/img/groups.png)

List of Topics for a Group
--------------------------

![Topic List](http://quantifind.github.io/KafkaOffsetMonitor/img/topics.png)

History of Topic position
-------------------------

![Position Graph](http://quantifind.github.io/KafkaOffsetMonitor/img/graph.png)

Running It
===========

If you do not want to build it manually, just download the [current jar](http://quantifind.github.io/KafkaOffsetMonitor/dist/KafkaOffsetMonitor-assembly-0.1.0-SNAPSHOT.jar).

This is a small webapp, you can run it locally or on a server, as long as you have access to the ZooKeeper nodes controlling kafka.

感谢李志涛同学，该webapp是在他的基础上进行修改，他的monitor只能对kafka的0.8.1版本进行监控，但是目前有好多系统都是kafka0.7.2版本，该监控系统是对kafka0.7.2进行监控，因为scala版本变更比较快，所以该监控系统处理了大量的kafka0.7.2的兼容问题，另外需要一个对kafka consumer端的代理

```
kafka consumer代理
java -jar ./lib/kafka-proxy-0.0.1-SNAPSHOT-jar-with-dependencies.jar &

kafka 监控
java -cp KafkaOffsetMonitor-assembly-0.1.0-SNAPSHOT.jar \
     com.quantifind.kafka.offsetapp.OffsetGetterWeb \
     --zk zk-server1,zk-server2 \
     --port 8080 \
     --refresh 10.seconds \
     --retain 2.days
```

The arguments are:

- **zk** the ZooKeeper hosts
- **port** on what port will the app be available
- **refresh** how often should the app refresh and store a point in the DB
- **retain** how long should points be kept in the DB
- **dbName** where to store the history (default 'offsetapp')
