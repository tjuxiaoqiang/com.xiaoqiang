package com.quantifind.kafka

import java.net._
import java.nio.channels._
import kafka.api._
import kafka.message._
import kafka.network._
import kafka.utils._

class SimpleConsumer(val host: String,
                     val port: Int,
                     val soTimeout: Int,
                     val bufferSize: Int) {
  
  private var channel : SocketChannel = null
  private val lock = new Object()

  private def connect(): SocketChannel = {
    close
    val address = new InetSocketAddress(host, port)

    val channel = SocketChannel.open
    channel.configureBlocking(true)
    channel.socket.setReceiveBufferSize(bufferSize)
    channel.socket.setSoTimeout(soTimeout)
    channel.socket.setKeepAlive(true)
    channel.socket.setTcpNoDelay(true)
    channel.connect(address)
    
    channel
  }
  
  def close() {
    lock synchronized {
      channel = null
    }
  }
}