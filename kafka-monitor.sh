#!/bin/bash
java -Xms512M -Xmx512M -Xss1024K -XX:PermSize=256m -XX:MaxPermSize=512m -cp /home/xiaoqiang/workspace/KafkaOffsetMonitor-0.2.0/target/scala-2.10/KafkaOffsetMonitor-assembly-0.1.0-SNAPSHOT.jar \
     com.quantifind.kafka.offsetapp.OffsetGetterWeb --zk localhost:2181 \
     --port 8086 \
     --refresh 10.seconds \
     --retain 7.days 1>kafka-monitor-logs/stdout.log 2>kafka-monitor-logs/stderr.log &
