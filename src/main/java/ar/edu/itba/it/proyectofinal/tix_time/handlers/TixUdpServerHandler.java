package ar.edu.itba.it.proyectofinal.tix_time.handlers;

import ar.edu.itba.it.proyectofinal.tix_time.data.TixDataPackage;
import ar.edu.itba.it.proyectofinal.tix_time.data.TixPackage;
import ar.edu.itba.it.proyectofinal.tix_time.data.TixTimestampPackage;
import ar.edu.itba.it.proyectofinal.tix_time.util.TixTimeUitl;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
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
			logger.info("It's data!");
		}
		if (msg instanceof TixTimestampPackage) {
			((TixTimestampPackage)msg).setReceptionTimestamp(TixTimeUitl.NANOS_OF_DAY.get());
			logger.info("It's a timestamp!");
			((TixTimestampPackage)msg).setSentTimestamp(TixTimeUitl.NANOS_OF_DAY.get());
		}
		ChannelFuture f = ctx.writeAndFlush(msg);
		f.addListener(ChannelFutureListener.CLOSE);
		logger.exit();
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
			throws Exception {
		logger.catching(cause);
		logger.error("Exception caught in channel", cause);
	}
}
