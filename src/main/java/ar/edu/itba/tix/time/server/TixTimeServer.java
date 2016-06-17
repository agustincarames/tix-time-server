package ar.edu.itba.tix.time.server;

import ar.edu.itba.tix.time.core.decoder.TixMessageDecoder;
import ar.edu.itba.tix.time.core.encoder.TixMessageEncoder;
import ar.edu.itba.tix.time.server.handler.TixUdpServerHandler;
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

import java.util.concurrent.Executors;

public class TixTimeServer {

	public static final int DEFAULT_WORKER_THREADS = Runtime.getRuntime().availableProcessors() * 2;
	public static final int DEFAULT_PORT = 4500;

	public static void main( String[] args ) {
		new TixTimeServer();
	}

	private final Logger logger = LogManager.getLogger(this.getClass());

	private TixTimeServer() {
		logger.info("Starting Server");
		EventLoopGroup workerGroup;
		Class<? extends Channel> datagramChannelClass;
		if (Epoll.isAvailable()) {
			logger.info("epoll available");
			workerGroup = new EpollEventLoopGroup(DEFAULT_WORKER_THREADS);
			datagramChannelClass = EpollDatagramChannel.class;
		} else {
			logger.info("epoll unavailable");
			logger.warn("epoll unavailable performance may be reduced due to single thread scheme.");
			workerGroup = new NioEventLoopGroup(DEFAULT_WORKER_THREADS, Executors.privilegedThreadFactory());
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
							ch.pipeline().addLast(new TixMessageDecoder());
							ch.pipeline().addLast(new TixUdpServerHandler());
							ch.pipeline().addLast(new TixMessageEncoder());
						}
					});
			if (Epoll.isAvailable()) {
				b.option(EpollChannelOption.SO_REUSEPORT, true);
			}
			ChannelFuture future;
			logger.info("Binding into port {}", DEFAULT_PORT);
			for (int i = 0; i < DEFAULT_WORKER_THREADS; i++) {
				future = b.bind(DEFAULT_PORT).sync().channel().closeFuture().await();
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
