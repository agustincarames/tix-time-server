package ar.edu.itba.it.proyectofinal.tix_time;

import ar.edu.itba.it.proyectofinal.tix_time.data.TixDataPackage;
import ar.edu.itba.it.proyectofinal.tix_time.data.TixPackage;
import ar.edu.itba.it.proyectofinal.tix_time.data.TixTimestampPackage;
import ar.edu.itba.it.proyectofinal.tix_time.decoder.TixMessageDecoder;
import ar.edu.itba.it.proyectofinal.tix_time.encoder.TixMessageEncoder;
import ar.edu.itba.it.proyectofinal.tix_time.util.TixTimeUitl;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.security.*;
import java.util.Base64;

import static org.junit.Assert.*;

public class TixTimeServerTest {

	private EmbeddedChannel embeddedChannel;
	private InetSocketAddress from;
	private InetSocketAddress to;
	private long initialTimestamp;
	private String publicKey;
	private String filename;
	private String message;
	private String signature;

	@Before
	public void setUp() throws Exception {
		embeddedChannel = new EmbeddedChannel(new TixMessageEncoder(), new TixMessageDecoder());
		from = InetSocketAddress.createUnresolved("localhost", 4500);
		to = InetSocketAddress.createUnresolved("localhost", 4501);
		initialTimestamp = TixTimeUitl.NANOS_OF_DAY.get();
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
			signature = new String(signer.sign());
		} catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
			e.printStackTrace();
		}

	}

	private <T extends TixPackage> T passThroughChannel(T message) {
		assertTrue(embeddedChannel.writeOutbound(message));
		Object o = embeddedChannel.readOutbound();
		assertNotNull(o);
		DatagramPacket datagramPacket = (DatagramPacket)o;
		assertTrue(embeddedChannel.writeInbound(datagramPacket));
		Object returnedMessage = embeddedChannel.readInbound();
		assertNotNull(returnedMessage);
		return (T)returnedMessage;
	}

	@Test
	public void shouldEncodeAndDecodeTixTimestampPackage() throws Exception {
		TixTimestampPackage timestampPackage = new TixTimestampPackage(from, to, initialTimestamp);
		TixTimestampPackage returnedTimestampPackage = passThroughChannel(timestampPackage);
		assertFalse(timestampPackage == returnedTimestampPackage);
		assertEquals(timestampPackage, returnedTimestampPackage);
	}

	@Test
	public void shouldEncodeAndDecodeTixDataPackage() throws Exception {
		TixDataPackage dataPackage = new TixDataPackage(from, to, initialTimestamp,
				publicKey, signature, filename, message);
		TixDataPackage returnedDataPackage = passThroughChannel(dataPackage);
		assertFalse(dataPackage == returnedDataPackage);
		assertEquals(dataPackage, returnedDataPackage);
	}
}
