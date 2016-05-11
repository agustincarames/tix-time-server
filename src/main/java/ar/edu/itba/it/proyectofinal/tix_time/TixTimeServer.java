package ar.edu.itba.it.proyectofinal.tix_time;

import ar.edu.itba.it.proyectofinal.tix_time.decoder.TixMessageDecoder;
import ar.edu.itba.it.proyectofinal.tix_time.encoder.TixMessageEncoder;
import ar.edu.itba.it.proyectofinal.tix_time.handlers.TixUdpServerHandler;
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

	private static final int WORKER_THREADS = Runtime.getRuntime().availableProcessors() * 2;
	private static final int PORT = 4500;

	public static void main( String[] args ) {
		new TixTimeServer();
    }

	private final Logger logger = LogManager.getLogger(this.getClass());
	
	private TixTimeServer() {
		EventLoopGroup workerGroup;
		Class<? extends Channel> datagramChannelClass;
		if (Epoll.isAvailable()) {
			logger.info("epoll available");
			workerGroup = new EpollEventLoopGroup(WORKER_THREADS);
			datagramChannelClass = EpollDatagramChannel.class;
		} else {
			logger.info("epoll unavailable");
			logger.warn("epoll unavailable performance may be reduced due to single thread scheme.");
			workerGroup = new NioEventLoopGroup(WORKER_THREADS, Executors.privilegedThreadFactory());
			datagramChannelClass = NioDatagramChannel.class;
		}
		
		try {
			logger.info("Setting up server");
			Bootstrap b = new Bootstrap();
			b.group(workerGroup)
			 .channel(datagramChannelClass)
			 .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
			 .option(ChannelOption.SO_BROADCAST, true)
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
			logger.info("Binding server into port {}", PORT);
			for (int i = 0; i < WORKER_THREADS; i++) {
				future = b.bind(PORT).sync().channel().closeFuture().await();
				if (!future.isSuccess()) {
					logger.error("Channel Future {} did not succeed", future);
					throw new Error("Channel Future did not succeed");
				}
			}
		} catch (InterruptedException e) {
			logger.fatal("Server interrupted", e);
			e.printStackTrace();
		} finally {
			logger.info("Server shutting down");
			workerGroup.shutdownGracefully();
		}
	}
}
