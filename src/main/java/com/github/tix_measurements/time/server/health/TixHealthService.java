package com.github.tix_measurements.time.server.health;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.concurrent.Future;

public class TixHealthService {

	private final int httpPort;
	
	private final ServerBootstrap httpBootstrap;
	private ChannelFuture httpFuture = null;
	private EventLoopGroup httpMasterGroup = null;
	private EventLoopGroup httpWorkerGroup = null;

	private final Logger logger = LogManager.getLogger(this.getClass());
	
	public TixHealthService(int httpPort) {
		this.httpPort = httpPort;
		this.httpBootstrap = new ServerBootstrap();
	}

	public void start() throws InterruptedException {
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
						ch.pipeline().addLast(new TixHealthServiceHandler());
					}
				});
		httpFuture = httpBootstrap.bind(httpPort).sync().channel().closeFuture();
	}

	public void stop() {
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
