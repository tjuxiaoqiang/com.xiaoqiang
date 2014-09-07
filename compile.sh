#!/bin/bash
./sbt assembly

rm -fr /home/xiaoqiang/workspace/KafkaOffsetMonitor-0.2.0/KafkaOffsetMonitor-assembly-0.1.0-SNAPSHOT.jar

cp /home/xiaoqiang/workspace/KafkaOffsetMonitor-0.2.0/target/scala-2.10/KafkaOffsetMonitor-assembly-0.1.0-SNAPSHOT.jar ./

./kafka-monitor.sh
