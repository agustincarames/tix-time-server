package com.github.tix_measurements.time.server.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tix_measurements.time.core.data.TixDataPacket;
import com.github.tix_measurements.time.core.data.TixPacket;
import com.github.tix_measurements.time.core.util.TixCoreUtils;
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
		TixPacket response;
		TixPacket incoming;
		long receptionTimestamp = TixCoreUtils.NANOS_OF_DAY.get();
		if (!(msg instanceof TixPacket)) {
			logger.error("Unexpected message type. " +
					"Expected instance of TixTimestampPackage, recieved message of type {}", msg.getClass().getName());
			throw new IllegalArgumentException("Expected a TixTimestampPackage");
		}
		incoming = (TixPacket) msg;
		if (msg instanceof TixDataPacket) {
			TixDataPacket dataIncoming = (TixDataPacket) incoming;
			response = new TixDataPacket(dataIncoming.getTo(), dataIncoming.getFrom(), dataIncoming.getInitialTimestamp(),
					dataIncoming.getPublicKey(), dataIncoming.getMessage(), dataIncoming.getSignature());
			ObjectMapper mapper = new ObjectMapper();
			byte[] bytes = mapper.writeValueAsBytes(dataIncoming);
			this.queueChannel.basicPublish("", queueName, null, bytes);
			logger.debug("Data sent to queue: " + new String(bytes));
		} else {
			response = new TixPacket(incoming.getTo(), incoming.getFrom(), incoming.getType(), incoming.getInitialTimestamp());
		}
		response.setReceptionTimestamp(receptionTimestamp);
		response.setSentTimestamp(TixCoreUtils.NANOS_OF_DAY.get());
		ctx.pipeline().writeAndFlush(response);
		logger.exit();
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
			throws Exception {
		logger.catching(cause);
		logger.error("Exception caught in channel", cause);
	}
}
