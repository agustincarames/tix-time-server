package com.github.tix_measurements.time.server;

import com.github.tix_measurements.time.server.handler.TixUdpServerHandler;
import com.github.tix_measurements.time.core.decoder.TixMessageDecoder;
import com.github.tix_measurements.time.core.encoder.TixMessageEncoder;
import com.rabbitmq.client.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.Channel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.concurrent.Executors;

@SpringBootApplication
public class TixTimeServer implements CommandLineRunner {

	public static void main( String[] args ) {
		SpringApplication.run(TixTimeServer.class, args);
	}

	private final Logger logger = LogManager.getLogger(this.getClass());

	@Value("${tix-time-server.queue-host}")
	private String queueHost;

	@Value("${tix-time-server.queue-name}")
	private String queueName;

	@Value("${tix-time-server.worker-threads-quantity}")
	private int workerThreadsQuantity;

	@Value("${tix-time-server.port}")
	private int port;

	@Override
	public void run(String... args) throws Exception {

		logger.info("Starting Server");
		EventLoopGroup workerGroup;
		Class<? extends Channel> datagramChannelClass;
		if (Epoll.isAvailable()) {
			logger.info("epoll available");
			workerGroup = new EpollEventLoopGroup(workerThreadsQuantity);
			datagramChannelClass = EpollDatagramChannel.class;
		} else {
			logger.info("epoll unavailable");
			logger.warn("epoll unavailable performance may be reduced due to single thread scheme.");
			workerGroup = new NioEventLoopGroup(workerThreadsQuantity, Executors.privilegedThreadFactory());
			datagramChannelClass = NioDatagramChannel.class;
		}

		try {
			logger.info("Setting up");
			Bootstrap b = new Bootstrap();
			b.group(workerGroup)
					.channel(datagramChannelClass)
					.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
					.handler(new ChannelInitializer<DatagramChannel>() {
						@Override
						protected void initChannel(DatagramChannel ch)
								throws Exception {

							ConnectionFactory connectionFactory = new ConnectionFactory();
							connectionFactory.setHost(queueHost);
							Connection queueConnection = connectionFactory.newConnection();
							logger.info("Connection with queue server established.");
							com.rabbitmq.client.Channel queueChannel = queueConnection.createChannel();
							queueChannel.queueDeclare(queueName, true, false, false, null); //Create or attach to the queue queueName, that is durable, non-exclusive and non auto-deletable1
							logger.info("Queue connected successfully");
							ch.pipeline().addLast(new TixMessageDecoder());
							ch.pipeline().addLast(new TixUdpServerHandler(queueConnection, queueChannel, queueName));
							ch.pipeline().addLast(new TixMessageEncoder());
						}
					});
			if (Epoll.isAvailable()) {
				b.option(EpollChannelOption.SO_REUSEPORT, true);
			}
			ChannelFuture future;
			logger.info("Binding into port {}", port);
			for (int i = 0; i < workerThreadsQuantity; i++) {
				future = b.bind(port).sync().channel().closeFuture().await();
				if (!future.isSuccess()) {
					logger.error("Channel Future {} did not succeed", future);
					throw new Error("Channel Future did not succeed");
				}
			}
		} catch (InterruptedException e) {
			logger.fatal("Interrupted", e);
			e.printStackTrace();
		} finally {
			logger.info("Shutting down");
			workerGroup.shutdownGracefully();
		}
	}
}
