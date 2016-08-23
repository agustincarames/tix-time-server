package ar.edu.itba.tix.time.server.handler;

import ar.edu.itba.tix.time.core.data.TixDataPackage;
import ar.edu.itba.tix.time.core.data.TixTimestampPackage;
import ar.edu.itba.tix.time.core.decoder.TixMessageDecoder;
import ar.edu.itba.tix.time.core.encoder.TixMessageEncoder;
import ar.edu.itba.tix.time.core.util.TixTimeUitl;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.*;
import java.util.Base64;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TixUdpServerHandlerTest {

	private EmbeddedChannel encoderDecoderChannel;
	private EmbeddedChannel testChannel;
	private InetSocketAddress from;
	private InetSocketAddress to;
	private Connection queueConnection;
	private Channel queueChannel;
	private String queueName;
	private String publicKey;
	private String filename;
	private String message;
	private byte[] signature;

	@Before
	public void setUp() throws Exception {
		queueConnection = mock(Connection.class);
		queueChannel = mock(Channel.class);
		queueName = "mocking-queue";
		encoderDecoderChannel = new EmbeddedChannel(
				new TixMessageDecoder(),
				new TixMessageEncoder());
		testChannel = new EmbeddedChannel(
				new TixMessageDecoder(),
				new TixUdpServerHandler(queueConnection, queueChannel, queueName),
				new TixMessageEncoder());
		from = new InetSocketAddress(InetAddress.getLocalHost(), 4500);
		to = new InetSocketAddress(InetAddress.getLocalHost(), 4501);
		setUpData();
	}

	private void setUpData() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(512);
			KeyPair keyPair = generator.genKeyPair();
			publicKey = new String(Base64.getEncoder().encode(keyPair.getPublic().getEncoded()));
			filename = "a";
			message = "a";
			Signature signer = Signature.getInstance("SHA1WithRSA");
			signer.initSign(keyPair.getPrivate());
			signer.update(message.getBytes());
			signature = signer.sign();
		} catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
			e.printStackTrace();
		}
	}

	private <T extends TixTimestampPackage> T passThroughChannel(T message) {
		DatagramPacket datagramPacket = encodeMessage(message);
		testChannel.writeInbound(datagramPacket);
		Object returnedDatagram = testChannel.readOutbound();
		T returnedMessage = decodeDatagram((DatagramPacket)returnedDatagram);
		return returnedMessage;
	}

	private <T extends TixTimestampPackage> T decodeDatagram(DatagramPacket datagramPacket) {
		assertThat(encoderDecoderChannel.writeInbound(datagramPacket)).isTrue();
		Object o = encoderDecoderChannel.readInbound();
		assertThat(o).isNotNull();
		return (T)o;
	}

	private <T extends TixTimestampPackage> DatagramPacket encodeMessage(T message) {
		assertThat(encoderDecoderChannel.writeOutbound(message)).isTrue();
		Object o = encoderDecoderChannel.readOutbound();
		assertThat(o).isNotNull();
		return (DatagramPacket)o;
	}

	@Test
	public void testTixTimestampPackage() {
		long initialTimestamp = TixTimeUitl.NANOS_OF_DAY.get();
		TixTimestampPackage timestampPackage = new TixTimestampPackage(from, to, initialTimestamp);
		TixTimestampPackage returnedTimestampPackage = passThroughChannel(timestampPackage);
		long finalTimestamp = TixTimeUitl.NANOS_OF_DAY.get();
		assertReturnedPackageTimestamps(timestampPackage, returnedTimestampPackage, finalTimestamp);
	}

	@Test
	public void testTixDataPackage() throws IOException {
		long initialTimestamp = TixTimeUitl.NANOS_OF_DAY.get();
		TixDataPackage dataPackage = new TixDataPackage(from, to, initialTimestamp,
				publicKey, filename, message, signature);
		TixDataPackage returnedDataPackage = passThroughChannel(dataPackage);
		long finalTimestamp = TixTimeUitl.NANOS_OF_DAY.get();
		assertReturnedPackageTimestamps(dataPackage, returnedDataPackage, finalTimestamp);
		verify(queueChannel).basicPublish("", queueName, null, dataPackage.toJson().getBytes());
	}

	private void assertReturnedPackageTimestamps(TixTimestampPackage originalPackage, TixTimestampPackage returnedPackage,
	                                             long finalTimestamp) {
		assertThat(returnedPackage.getFrom()).isEqualTo(originalPackage.getTo());
		assertThat(returnedPackage.getTo()).isEqualTo(originalPackage.getFrom());
		assertThat(returnedPackage.getInitalTimestamp()).isEqualTo(originalPackage.getInitalTimestamp());

		Stream.of(returnedPackage.getReceptionTimestamp(), returnedPackage.getSentTimestamp())
				.forEach(internalTimestamp -> {
					assertThat(internalTimestamp).isNotZero();
					assertThat(internalTimestamp).isPositive();
					assertThat(internalTimestamp).isGreaterThan(originalPackage.getInitalTimestamp());
					assertThat(internalTimestamp).isLessThan(finalTimestamp);
				});

		assertThat(returnedPackage.getReceptionTimestamp()).isLessThanOrEqualTo(returnedPackage.getSentTimestamp());
		assertThat(returnedPackage.getFinalTimestamp()).isZero();
	}
}
