package ar.edu.itba.it.proyectofinal.tix_time;

import ar.edu.itba.it.proyectofinal.tix_time.encoder.TixMessageEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

import java.util.concurrent.Executors;

import ar.edu.itba.it.proyectofinal.tix_time.decoder.TixMessageDecoder;
import ar.edu.itba.it.proyectofinal.tix_time.handlers.TixUdpClientMessageHandler;

public class TixTimeServer {

	private static final int WORKER_THREADS = Runtime.getRuntime().availableProcessors() * 2;

	public static void main( String[] args ) {
		new TixTimeServer();
    }
	
	private TixTimeServer() {
		EventLoopGroup workerGroup;
		Class<? extends Channel> datagramChannelClass;
		if (Epoll.isAvailable()) {
			workerGroup = new EpollEventLoopGroup(WORKER_THREADS);
			datagramChannelClass = EpollDatagramChannel.class;
		} else {
			workerGroup = new NioEventLoopGroup(WORKER_THREADS, Executors.privilegedThreadFactory());
			datagramChannelClass = NioDatagramChannel.class;
		}
		
		try {
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
					ch.pipeline().addLast(new TixUdpClientMessageHandler());
					ch.pipeline().addLast(new TixMessageEncoder());
				}
			});
			if (Epoll.isAvailable()) {
				b.option(EpollChannelOption.SO_REUSEPORT, true);
			}
			ChannelFuture future;
			for (int i = 0; i < WORKER_THREADS; i++) {
				future = b.bind(4500).sync().channel().closeFuture().await();
				if (!future.isSuccess()) {
					throw new Error("Changos");
				}
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			workerGroup.shutdownGracefully();
		}
	}
}
