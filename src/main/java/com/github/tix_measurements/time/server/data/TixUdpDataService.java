package com.github.tix_measurements.time.server.data;

import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.tix_measurements.time.core.decoder.TixMessageDecoder;
import com.github.tix_measurements.time.core.encoder.TixMessageEncoder;
import com.github.tix_measurements.time.server.TixQueueConnection;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.concurrent.Future;

public class TixUdpDataService {

	private static final int BUFFER_ALLOCATION_SIZE = 4096 + 1024; // XXX: https://stackoverflow.com/questions/13086564/netty-increase-channelbuffer-size?answertab=votes#tab-top

	private final int workerThreadsQuantity;
	private final int udpPort;
	private final TixQueueConnection queue;
	
	private final Bootstrap udpBootstrap;
	private EventLoopGroup udpWorkerGroup = null;
	private ChannelFuture[] udpFutures = null;

	private final Logger logger = LogManager.getLogger(this.getClass());
	
	public TixUdpDataService(int udpPort, int workerThreadsQuantity, TixQueueConnection queue) {
		this.workerThreadsQuantity = workerThreadsQuantity;
		this.udpPort = udpPort;
		this.queue = queue;
		this.udpBootstrap = new Bootstrap();
	}

	public void start() throws InterruptedException {
		Class<? extends Channel> datagramChannelClass;
		if (Epoll.isAvailable()) {
			logger.info("epoll available");
			udpWorkerGroup = new EpollEventLoopGroup(workerThreadsQuantity);
			datagramChannelClass = EpollDatagramChannel.class;
			this.udpFutures = new ChannelFuture[workerThreadsQuantity];
		} else {
			logger.warn("epoll unavailable, performance may be reduced due to single thread scheme.");
			udpWorkerGroup = new NioEventLoopGroup(workerThreadsQuantity, Executors.privilegedThreadFactory());
			datagramChannelClass = NioDatagramChannel.class;
			this.udpFutures = new ChannelFuture[1];
		}

		logger.info("Setting up");
		udpBootstrap.group(udpWorkerGroup)
				.channel(datagramChannelClass)
				.option(ChannelOption.SO_RCVBUF, BUFFER_ALLOCATION_SIZE)
				.option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(BUFFER_ALLOCATION_SIZE))
				.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
				.handler(new ChannelInitializer<DatagramChannel>() {
					@Override
					protected void initChannel(DatagramChannel ch)
							throws Exception {
						logger.info("Connection with queue server established.");
						com.rabbitmq.client.Channel queueChannel = queue.getConnection().createChannel();
						queueChannel.queueDeclare(queue.getName(), true, false, false, null); //Create or attach to the queue queueName, that is durable, non-exclusive and non auto-deletable1
						logger.info("Queue connected successfully");
						ch.pipeline().addLast(new TixMessageDecoder());
						ch.pipeline().addLast(new TixUdpDataServiceHandler(queueChannel, queue.getName()));
						ch.pipeline().addLast(new TixMessageEncoder());
					}
				});

		logger.info("Binding UDP into port {}", udpPort);
		if (Epoll.isAvailable()) {
			udpBootstrap.option(EpollChannelOption.SO_REUSEPORT, true);
		}
		for (int i = 0; i < udpFutures.length; i++) {
			udpFutures[i] = udpBootstrap.bind(udpPort).sync().channel().closeFuture();
		}
	}

	public void stop() {
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

	private void shutdownGroup(EventLoopGroup group) {
		if (group != null) {
			Future<?> f = group.shutdownGracefully().awaitUninterruptibly();
			if (!f.isSuccess()) {
				logger.warn("Could not shutdown group");
				throw new Error("Could not shutdown group");
			}
		}
	}
}
