package ar.edu.itba.tix.time.server.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tix_measurements.time.core.data.TixDataPacket;
import com.github.tix_measurements.time.core.data.TixTimestampPacket;
import com.github.tix_measurements.time.core.util.TixTimeUtils;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class TixUdpServerHandler extends ChannelInboundHandlerAdapter {

	private final Logger logger = LogManager.getLogger(this.getClass());
	private final Connection queueConnection;
	private final Channel queueChannel;
	private final String queueName;

	public TixUdpServerHandler(Connection queueConnection, Channel queueChannel, String queueName) throws IOException, TimeoutException {
		this.queueConnection = queueConnection;
		this.queueChannel = queueChannel;
		this.queueName = queueName;
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg)
			throws Exception {
		logger.entry(ctx, msg);
		TixTimestampPacket response;
		TixTimestampPacket incoming;
		long receptionTimestamp = TixTimeUtils.NANOS_OF_DAY.get();
		if (!(msg instanceof TixTimestampPacket)) {
			logger.error("Unexpected message type. " +
					"Expected instance of TixTimestampPackage, recieved message of type {}", msg.getClass().getName());
			throw new IllegalArgumentException("Expected a TixTimestampPackage");
		}
		incoming = (TixTimestampPacket) msg;
		if (msg instanceof TixDataPacket) {
			TixDataPacket dataIncoming = (TixDataPacket) incoming;
			response = new TixDataPacket(dataIncoming.getTo(), dataIncoming.getFrom(), dataIncoming.getInitialTimestamp(),
					dataIncoming.getPublicKey(), dataIncoming.getFilename(), dataIncoming.getMessage(), dataIncoming.getSignature());
			ObjectMapper mapper = new ObjectMapper();
			String dataIncomingJson = mapper.writeValueAsString(dataIncoming);
			this.queueChannel.basicPublish("", queueName, null, dataIncomingJson.getBytes());
			logger.debug("Data sent to queue: " + dataIncomingJson);
		} else {
			response = new TixTimestampPacket(incoming.getTo(), incoming.getFrom(), incoming.getInitialTimestamp());
		}
		response.setReceptionTimestamp(receptionTimestamp);
		response.setSentTimestamp(TixTimeUtils.NANOS_OF_DAY.get());
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
