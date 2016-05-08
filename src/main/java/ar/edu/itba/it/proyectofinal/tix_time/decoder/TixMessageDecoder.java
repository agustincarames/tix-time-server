package ar.edu.itba.it.proyectofinal.tix_time.decoder;

import ar.edu.itba.it.proyectofinal.tix_time.data.TixDataPackage;
import ar.edu.itba.it.proyectofinal.tix_time.data.TixPackage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;

import java.util.List;

import ar.edu.itba.it.proyectofinal.tix_time.data.TixTimestampPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TixMessageDecoder extends MessageToMessageDecoder<DatagramPacket> {
	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	@Override
	protected void decode(ChannelHandlerContext ctx, DatagramPacket msg,
			List<Object> out) throws Exception {
		ByteBuf payload = msg.content();
		TixPackage tixPackage;
		final long initialTimestamp = TixTimestampPackage.TIMESTAMP_READER.apply(payload);
		final long receivedTimestamp = TixTimestampPackage.TIMESTAMP_READER.apply(payload);
		final long sentTimestamp = TixTimestampPackage.TIMESTAMP_READER.apply(payload);
		final long finalTimestamp = TixTimestampPackage.TIMESTAMP_READER.apply(payload);
		if (payload.isReadable()) {
			String rawData = payload.readBytes(payload.readableBytes()).toString(CharsetUtil.UTF_8).trim();
			if (StringUtil.isNullOrEmpty(rawData)) {
				logger.warn("Empty data on a TixDataPackage");
				throw new IllegalArgumentException("empty data on TixDataPackage");
			}
			String[] data = rawData.split(TixDataPackage.DATA_DELIMITER);
			if (data[0].equals(TixDataPackage.DATA_HEADER)) {
				final String publicKey = TixDataPackage.DECODER.apply(data[1].trim()).trim();
				final String signature = TixDataPackage.DECODER.apply(data[2].trim()).trim();
				final String logFileName = TixDataPackage.DECODER.apply(data[3].trim()).trim();
				final String message = TixDataPackage.DECODER.apply(data[4].trim()).trim();
				tixPackage = new TixDataPackage(initialTimestamp, finalTimestamp, publicKey, signature, logFileName, message);
			} else {
				logger.warn("Malformed data package received {}", rawData);
				throw new IllegalArgumentException("Malformed data package received " + rawData);
			}
		} else {
			tixPackage = new TixTimestampPackage(initialTimestamp, finalTimestamp);
		}
		out.add(tixPackage);
	}

}
