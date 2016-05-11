package ar.edu.itba.it.proyectofinal.tix_time.handlers;

import ar.edu.itba.it.proyectofinal.tix_time.data.TixDataPackage;
import ar.edu.itba.it.proyectofinal.tix_time.data.TixPackage;
import ar.edu.itba.it.proyectofinal.tix_time.data.TixTimestampPackage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TixUdpServerHandler extends ChannelInboundHandlerAdapter {

	private final Logger logger = LogManager.getLogger(this.getClass());

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg)
			throws Exception {
		logger.entry(ctx, msg);
		if (!(msg instanceof TixPackage)) {
			logger.error("Unexpected message type. " +
					"Expected instance of TixPackage, recieved message of type {}", msg.getClass().getName());
			throw new IllegalArgumentException("Expected a TixPackage");
		}
		if (msg instanceof TixDataPackage) {
			System.out.println("It's data!");
		}
		if (msg instanceof TixTimestampPackage) {
			System.out.println("It's a timestamp!");
		}
		logger.exit();
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
			throws Exception {
		logger.catching(cause);
		logger.error("Exception caught in channel", cause);
	}
}
