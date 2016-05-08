package ar.edu.itba.it.proyectofinal.tix_time.encoder;

import ar.edu.itba.it.proyectofinal.tix_time.data.TixDataPackage;
import ar.edu.itba.it.proyectofinal.tix_time.data.TixPackage;
import ar.edu.itba.it.proyectofinal.tix_time.data.TixTimestampPackage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TixMessageEncoder extends MessageToByteEncoder<TixPackage> {
	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	@Override
	protected void encode(ChannelHandlerContext ctx, TixPackage msg, ByteBuf out) throws Exception {
		if (msg instanceof TixTimestampPackage) {
			TixTimestampPackage.TIMESTAMP_WRITER.apply(out, ((TixTimestampPackage)msg).getInitalTimestamp());
			TixTimestampPackage.TIMESTAMP_WRITER.apply(out, ((TixTimestampPackage)msg).getReceptionTimestamp());
			TixTimestampPackage.TIMESTAMP_WRITER.apply(out, ((TixTimestampPackage)msg).getSentTimestamp());
			TixTimestampPackage.TIMESTAMP_WRITER.apply(out, ((TixTimestampPackage)msg).getFinalTimestamp());
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
			out.writeBytes(data.getBytes(CharsetUtil.UTF_8));
		}
	}
}
