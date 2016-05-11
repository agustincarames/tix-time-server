package ar.edu.itba.it.proyectofinal.tix_time.encoder;

import ar.edu.itba.it.proyectofinal.tix_time.data.TixDataPackage;
import ar.edu.itba.it.proyectofinal.tix_time.data.TixPackage;
import ar.edu.itba.it.proyectofinal.tix_time.data.TixTimestampPackage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.util.CharsetUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class TixMessageEncoder extends MessageToMessageEncoder<TixPackage> {
	private final Logger logger = LogManager.getLogger(this.getClass());

	@Override
	protected void encode(ChannelHandlerContext ctx, TixPackage msg, List<Object> out) throws Exception {
		logger.entry(ctx, msg, out);
		DatagramPacket packet = new DatagramPacket(Unpooled.buffer(), msg.getFrom(), msg.getTo());
		if (msg instanceof TixTimestampPackage) {
			TixTimestampPackage.TIMESTAMP_WRITER.apply(packet.content(), ((TixTimestampPackage)msg).getInitalTimestamp());
			TixTimestampPackage.TIMESTAMP_WRITER.apply(packet.content(), ((TixTimestampPackage)msg).getReceptionTimestamp());
			TixTimestampPackage.TIMESTAMP_WRITER.apply(packet.content(), ((TixTimestampPackage)msg).getSentTimestamp());
			TixTimestampPackage.TIMESTAMP_WRITER.apply(packet.content(), ((TixTimestampPackage)msg).getFinalTimestamp());
		}
		if (msg instanceof TixDataPackage) {
			String data = TixDataPackage.DATA_HEADER +
					TixDataPackage.DATA_DELIMITER +
					TixDataPackage.ENCODER.apply(((TixDataPackage) msg).getPublicKey()) +
					TixDataPackage.DATA_DELIMITER +
					TixDataPackage.ENCODER.apply(((TixDataPackage) msg).getSignature()) +
					TixDataPackage.DATA_DELIMITER +
					TixDataPackage.ENCODER.apply(((TixDataPackage) msg).getFilename()) +
					TixDataPackage.DATA_DELIMITER +
					TixDataPackage.ENCODER.apply(((TixDataPackage) msg).getMessage()) +
					TixDataPackage.DATA_DELIMITER;
			packet.content().writeBytes(data.getBytes(CharsetUtil.UTF_8));
		}
		out.add(packet);
		logger.exit(out);
	}
}
