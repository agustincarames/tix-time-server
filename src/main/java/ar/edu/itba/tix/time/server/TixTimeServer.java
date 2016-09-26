package ar.edu.itba.tix.time.server;

import ar.edu.itba.tix.time.server.configuration.ConfigurationService;
import ar.edu.itba.tix.time.server.handler.TixUdpServerHandler;
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

import java.util.concurrent.Executors;

public class TixTimeServer {
	private final ConfigurationService configs = ConfigurationService.INSTANCE;

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
			workerGroup = new EpollEventLoopGroup(configs.workerThreadsQuantity());
			datagramChannelClass = EpollDatagramChannel.class;
		} else {
			logger.info("epoll unavailable");
			logger.warn("epoll unavailable performance may be reduced due to single thread scheme.");
			workerGroup = new NioEventLoopGroup(configs.workerThreadsQuantity(), Executors.privilegedThreadFactory());
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
							connectionFactory.setHost(configs.queueHost());
							Connection queueConnection = connectionFactory.newConnection();
							logger.info("Connection with queue server established.");
							com.rabbitmq.client.Channel queueChannel = queueConnection.createChannel();
							queueChannel.queueDeclare(configs.queueName(), true, false, false, null); //Create or attach to the queue queueName, that is durable, non-exclusive and non auto-deletable1
							logger.info("Queue connected successfully");
							ch.pipeline().addLast(new TixMessageDecoder());
							ch.pipeline().addLast(new TixUdpServerHandler(queueConnection, queueChannel, configs.queueName()));
							ch.pipeline().addLast(new TixMessageEncoder());
						}
					});
			if (Epoll.isAvailable()) {
				b.option(EpollChannelOption.SO_REUSEPORT, true);
			}
			ChannelFuture future;
			logger.info("Binding into port {}", configs.port());
			for (int i = 0; i < configs.workerThreadsQuantity(); i++) {
				future = b.bind(configs.port()).sync().channel().closeFuture().await();
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
