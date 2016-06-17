package ar.edu.itba.tix.time.server.handler;

import ar.edu.itba.tix.time.core.data.TixDataPackage;
import ar.edu.itba.tix.time.core.data.TixTimestampPackage;
import ar.edu.itba.tix.time.core.util.TixTimeUitl;
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
		TixTimestampPackage response;
		TixTimestampPackage incoming;
		if (!(msg instanceof TixTimestampPackage)) {
			logger.error("Unexpected message type. " +
					"Expected instance of TixTimestampPackage, recieved message of type {}", msg.getClass().getName());
			throw new IllegalArgumentException("Expected a TixTimestampPackage");
		}
		incoming = (TixTimestampPackage) msg;
		if (msg instanceof TixDataPackage) {
			TixDataPackage dataIncoming = (TixDataPackage) incoming;
			response = new TixDataPackage(dataIncoming.getTo(), dataIncoming.getFrom(), dataIncoming.getInitalTimestamp(),
					dataIncoming.getPublicKey(), dataIncoming.getSignature(), dataIncoming.getFilename(), dataIncoming.getMessage());
			logger.info("It's data!");
		} else {
			response = new TixTimestampPackage(incoming.getTo(), incoming.getFrom(), incoming.getInitalTimestamp());
		}
		response.setReceptionTimestamp(TixTimeUitl.NANOS_OF_DAY.get());
		logger.info("It's a timestamp!");
		response.setSentTimestamp(TixTimeUitl.NANOS_OF_DAY.get());
		ctx.pipeline().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
		logger.exit();
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
			throws Exception {
		logger.catching(cause);
		logger.error("Exception caught in channel", cause);
	}
}
