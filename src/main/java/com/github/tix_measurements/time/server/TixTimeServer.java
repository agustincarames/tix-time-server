package com.github.tix_measurements.time.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tix_measurements.time.core.decoder.TixMessageDecoder;
import com.github.tix_measurements.time.core.encoder.TixMessageEncoder;
import com.github.tix_measurements.time.server.config.ConfigurationManager;
import com.github.tix_measurements.time.server.handler.TixHttpServerHandler;
import com.github.tix_measurements.time.server.handler.TixUdpServerHandler;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.concurrent.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.Executors;

public class TixTimeServer {


	private final Logger logger = LogManager.getLogger(this.getClass());

	private final String queueHost;

	private final String queueName;

	private final int workerThreadsQuantity;

	private final int udpPort;

	private final int httpPort;

	private final ChannelFuture[] udpFutures;

	private final Bootstrap udpBootstrap;

	private final ServerBootstrap httpBootstrap;

	private EventLoopGroup udpWorkerGroup = null;

	private ChannelFuture httpFuture = null;
	private EventLoopGroup httpMasterGroup = null;
	private EventLoopGroup httpWorkerGroup = null;

	public static void main(String[] args) throws FileNotFoundException, InterruptedException {
		ConfigurationManager configs = new ConfigurationManager("TIX");
		configs.loadConfigs();
		TixTimeServer server = new TixTimeServer(configs.getString("queue.host"),
				configs.getString("queue.name"),
				configs.getInt("worker-threads-quantity"),
				configs.getInt("udp-port"),
				configs.getInt("http-port"));
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
		// TODO: Fix this, for some reason the server is not terminating autonomously. Making it exit.
		System.exit(1);
	}

	public TixTimeServer(String queueHost, String queueName, int workerThreadsQuantity, int udpPort, int httpPort) {
		this.queueHost = queueHost;
		this.queueName = queueName;
		this.workerThreadsQuantity = workerThreadsQuantity;
		this.udpPort = udpPort;
		this.httpPort = httpPort;
		this.udpFutures = new ChannelFuture[this.workerThreadsQuantity];
		this.udpBootstrap = new Bootstrap();
		this.httpBootstrap = new ServerBootstrap();
	}

	private void startTixServer() throws InterruptedException {
		Class<? extends Channel> datagramChannelClass;
		if (Epoll.isAvailable()) {
			logger.info("epoll available");
			udpWorkerGroup = new EpollEventLoopGroup(workerThreadsQuantity);
			datagramChannelClass = EpollDatagramChannel.class;
		} else {
			logger.info("epoll unavailable");
			logger.warn("epoll unavailable performance may be reduced due to single thread scheme.");
			udpWorkerGroup = new NioEventLoopGroup(workerThreadsQuantity, Executors.privilegedThreadFactory());
			datagramChannelClass = NioDatagramChannel.class;
		}

		logger.info("Setting up");
		udpBootstrap.group(udpWorkerGroup)
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
			udpBootstrap.option(EpollChannelOption.SO_REUSEPORT, true);
		}
		logger.info("Binding UDP into port {}", udpPort);
		for (int i = 0; i < udpFutures.length; i++) {
			udpFutures[i] = udpBootstrap.bind(udpPort).sync().channel().closeFuture();
		}
	}

	private void startHttpServer() throws InterruptedException {
		httpMasterGroup = new NioEventLoopGroup(1);
		httpWorkerGroup = new NioEventLoopGroup();
		httpBootstrap
				.group(httpMasterGroup, httpWorkerGroup)
				.channel(NioServerSocketChannel.class)
				.option(ChannelOption.SO_BACKLOG, 128)
				.childOption(ChannelOption.SO_KEEPALIVE, true)
				.childHandler(new ChannelInitializer<SocketChannel>() {
					@Override
					protected void initChannel(SocketChannel ch) throws Exception {
						ch.pipeline().addLast(new HttpServerCodec());
						ch.pipeline().addLast(new HttpObjectAggregator(512 * 1024));
						ch.pipeline().addLast(new TixHttpServerHandler());
					}
				});
		httpFuture = httpBootstrap.bind(httpPort).sync().channel().closeFuture();
	}

	public void start() {
		logger.info("Starting Server");
		try {
			startTixServer();
			startHttpServer();
		} catch (InterruptedException e) {
			logger.fatal("Interrupted", e);
			logger.catching(e);
			this.stop();
		}
	}

	private void shutdownGroup(EventLoopGroup group) {
		if (group != null) {
			Future f = group.shutdownGracefully().awaitUninterruptibly();
			if (!f.isSuccess()) {
				logger.warn("Could not shutdown group");
				throw new Error("Could not shutdown group");
			}
		}
	}

	private void stopUdpServer() {
		logger.info("Shutting down UDP server");
		if (udpWorkerGroup != null) {
			try {
				shutdownGroup(udpWorkerGroup);
			} catch (Error e) {
				logger.error("Could not shutdown UDP Server");
				throw new Error("Could not shutdown UDP Server", e);
			}
			for (int i = 0; i < workerThreadsQuantity; i++) {
				udpFutures[i].awaitUninterruptibly();
				if (!udpFutures[i].isSuccess()) {
					logger.error("Channel Future {} did not succeed", udpFutures[i]);
					throw new Error("Channel Future did not succeed");
				}
			}
		}
		logger.info("UDP server shutdown");
	}

	private void stopHttpServer() {
		logger.info("Shutting down HTTP server");
		try {
			shutdownGroup(httpWorkerGroup);
			shutdownGroup(httpMasterGroup);
		} catch (Error e) {
			logger.error("Could not shutdown HTTP Server");
			throw new Error("Could not shutdown HTTP Server", e);
		}
		if (httpFuture != null) {
			httpFuture.awaitUninterruptibly();
			if (!httpFuture.isSuccess()) {
				logger.error("Channel Future {} did not succeed", httpFuture);
				throw new Error("Channel Future did not succeed");
			}
		}
		logger.info("HTTP server shutdown");
	}

	public void stop() {
		logger.info("Shutting down");
		stopUdpServer();
		stopHttpServer();
		logger.info("Server shutdown");

	}

	public int getPort() {
		return this.udpPort;
	}
}
