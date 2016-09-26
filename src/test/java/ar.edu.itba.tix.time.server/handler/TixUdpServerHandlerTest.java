package ar.edu.itba.tix.time.server.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tix_measurements.time.core.data.TixDataPacket;
import com.github.tix_measurements.time.core.data.TixTimestampPacket;
import com.github.tix_measurements.time.core.decoder.TixMessageDecoder;
import com.github.tix_measurements.time.core.encoder.TixMessageEncoder;
import com.github.tix_measurements.time.core.util.TixTimeUtils;
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

	private <T extends TixTimestampPacket> T passThroughChannel(T message) {
		DatagramPacket datagramPacket = encodeMessage(message);
		testChannel.writeInbound(datagramPacket);
		Object returnedDatagram = testChannel.readOutbound();
		T returnedMessage = decodeDatagram((DatagramPacket)returnedDatagram);
		return returnedMessage;
	}

	private <T extends TixTimestampPacket> T decodeDatagram(DatagramPacket datagramPacket) {
		assertThat(encoderDecoderChannel.writeInbound(datagramPacket)).isTrue();
		Object o = encoderDecoderChannel.readInbound();
		assertThat(o).isNotNull();
		return (T)o;
	}

	private <T extends TixTimestampPacket> DatagramPacket encodeMessage(T message) {
		assertThat(encoderDecoderChannel.writeOutbound(message)).isTrue();
		Object o = encoderDecoderChannel.readOutbound();
		assertThat(o).isNotNull();
		return (DatagramPacket)o;
	}

	@Test
	public void testTixTimestampPackage() {
		long initialTimestamp = TixTimeUtils.NANOS_OF_DAY.get();
		TixTimestampPacket timestampPackage = new TixTimestampPacket(from, to, initialTimestamp);
		TixTimestampPacket returnedTimestampPackage = passThroughChannel(timestampPackage);
		long finalTimestamp = TixTimeUtils.NANOS_OF_DAY.get();
		assertReturnedPackageTimestamps(timestampPackage, returnedTimestampPackage, finalTimestamp);
	}

	@Test
	public void testTixDataPackage() throws IOException {
		long initialTimestamp = TixTimeUtils.NANOS_OF_DAY.get();
		TixDataPacket dataPackage = new TixDataPacket(from, to, initialTimestamp,
				publicKey, filename, message, signature);
		TixDataPacket returnedDataPackage = passThroughChannel(dataPackage);
		long finalTimestamp = TixTimeUtils.NANOS_OF_DAY.get();
		assertReturnedPackageTimestamps(dataPackage, returnedDataPackage, finalTimestamp);
		ObjectMapper mapper = new ObjectMapper();
		verify(queueChannel).basicPublish("", queueName, null, mapper.writeValueAsBytes(dataPackage));
	}

	private void assertReturnedPackageTimestamps(TixTimestampPacket originalPackage, TixTimestampPacket returnedPackage,
	                                             long finalTimestamp) {
		assertThat(returnedPackage.getFrom()).isEqualTo(originalPackage.getTo());
		assertThat(returnedPackage.getTo()).isEqualTo(originalPackage.getFrom());
		assertThat(returnedPackage.getInitialTimestamp()).isEqualTo(originalPackage.getInitialTimestamp());

		Stream.of(returnedPackage.getReceptionTimestamp(), returnedPackage.getSentTimestamp())
				.forEach(internalTimestamp -> {
					assertThat(internalTimestamp).isNotZero();
					assertThat(internalTimestamp).isPositive();
					assertThat(internalTimestamp).isGreaterThan(originalPackage.getInitialTimestamp());
					assertThat(internalTimestamp).isLessThan(finalTimestamp);
				});

		assertThat(returnedPackage.getReceptionTimestamp()).isLessThanOrEqualTo(returnedPackage.getSentTimestamp());
		assertThat(returnedPackage.getFinalTimestamp()).isZero();
	}
}
