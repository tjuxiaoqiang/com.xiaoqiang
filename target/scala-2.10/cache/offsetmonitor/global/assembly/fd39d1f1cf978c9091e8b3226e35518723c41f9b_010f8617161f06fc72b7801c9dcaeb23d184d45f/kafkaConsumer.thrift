namespace java com.xiaonei.kafka.consumer

service SimpleConsumerProxy {
	list<i64> getOffsetsBefore(1:string topic, 2:i32 partition, 3:i64 time, 4:i32 maxNumOffsets, 
		5:string host, 6:i32 port)
}
