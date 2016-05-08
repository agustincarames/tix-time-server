package ar.edu.itba.it.proyectofinal.tix_time.decoder;

import ar.edu.itba.it.proyectofinal.tix_time.data.TixDataPackage;
import ar.edu.itba.it.proyectofinal.tix_time.data.TixPackage;
import ar.edu.itba.it.proyectofinal.tix_time.data.TixTimestampPackage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class TixMessageDecoder extends MessageToMessageDecoder<DatagramPacket> {
	private final Logger logger = LogManager.getLogger(this.getClass());

	@Override
	protected void decode(ChannelHandlerContext ctx, DatagramPacket msg,
			List<Object> out) throws Exception {
		logger.entry(ctx, msg, out);
		ByteBuf payload = msg.content();
		TixPackage tixPackage;
		final long initialTimestamp = TixTimestampPackage.TIMESTAMP_READER.apply(payload);
		final long receivedTimestamp = TixTimestampPackage.TIMESTAMP_READER.apply(payload);
		final long sentTimestamp = TixTimestampPackage.TIMESTAMP_READER.apply(payload);
		final long finalTimestamp = TixTimestampPackage.TIMESTAMP_READER.apply(payload);
		if (payload.isReadable()) {
			String rawData = payload.readBytes(payload.readableBytes()).toString(CharsetUtil.UTF_8).trim();
			if (StringUtil.isNullOrEmpty(rawData)) {
				logger.error("Empty data on a TixDataPackage");
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
				logger.error("Malformed data package received {}", rawData);
				throw new IllegalArgumentException("Malformed data package received " + rawData);
			}
		} else {
			tixPackage = new TixTimestampPackage(initialTimestamp, finalTimestamp);
			((TixTimestampPackage)tixPackage).setReceptionTimestamp(receivedTimestamp);
			((TixTimestampPackage)tixPackage).setSentTimestamp(sentTimestamp);
		}
		out.add(tixPackage);
		logger.exit(tixPackage);
	}

}
