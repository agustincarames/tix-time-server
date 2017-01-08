package com.github.tix_measurements.time.server;

import com.github.tix_measurements.time.core.decoder.TixMessageDecoder;
import com.github.tix_measurements.time.core.encoder.TixMessageEncoder;
import com.github.tix_measurements.time.server.config.ConfigurationManager;
import com.github.tix_measurements.time.server.handler.TixUdpServerHandler;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Executors;

public class TixTimeServer {


	private final Logger logger = LogManager.getLogger(this.getClass());

	private final String queueHost;

	private final String queueName;

	private final int workerThreadsQuantity;

	private final int port;

	private final ChannelFuture[] futures;

	private final Bootstrap bootstrap;

	private EventLoopGroup workerGroup = null;

	public static void main(String[] args) throws FileNotFoundException {
		ConfigurationManager configs = new ConfigurationManager("TIX");
		configs.loadConfigs();
		TixTimeServer server = new TixTimeServer(configs.getString("queue.host"),
				configs.getString("queue.name"),
				configs.getInt("worker-threads-quantity"),
				configs.getInt("port"));
		server.start();
		System.out.println("Press enter to terminate");
		try {
			while(System.in.available() == 0) {
				Thread.sleep(10);
			}
		} catch (IOException | InterruptedException e) {
			server.logger.catching(e);
			server.logger.fatal("Unexpected exception", e);
		} finally {
			server.stop();
		}
	}

	public TixTimeServer(String queueHost, String queueName, int workerThreadsQuantity, int port) {
		this.queueHost = queueHost;
		this.queueName = queueName;
		this.workerThreadsQuantity = workerThreadsQuantity;
		this.port = port;
		this.futures = new ChannelFuture[this.workerThreadsQuantity];
		this.bootstrap = new Bootstrap();
	}

	public void start() {
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
			bootstrap.group(workerGroup)
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
				bootstrap.option(EpollChannelOption.SO_REUSEPORT, true);
			}
			logger.info("Binding into port {}", port);
			for (int i = 0; i < futures.length; i++) {
				futures[i] = bootstrap.bind(port).sync().channel().closeFuture();
			}
		} catch (InterruptedException e) {
			logger.fatal("Interrupted", e);
			logger.catching(e);
			this.stop();
		}
	}

	public void stop() {
		if (workerGroup != null) {
			for (int i = 0; i < workerThreadsQuantity; i++) {
				try {
					futures[i].await();
					if (futures[i] != null && !futures[i].isSuccess()) {
						logger.error("Channel Future {} did not succeed", futures[i]);
						throw new Error("Channel Future did not succeed");
					}
				} catch (InterruptedException e) {
					logger.error("ChannelFuture error");
					logger.catching(e);
				}
			}
			logger.info("Shutting down");
			workerGroup.shutdownGracefully();
		}
	}

	public int getPort() {
		return this.port;
	}
}
