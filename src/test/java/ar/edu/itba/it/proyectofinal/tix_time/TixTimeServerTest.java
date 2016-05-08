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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import static org.junit.Assert.*;

public class TixTimeServerTest {

	private EmbeddedChannel embeddedChannel;

	@Before
	public void setUp() throws Exception {
		embeddedChannel = new EmbeddedChannel(new TixMessageEncoder(), new TixMessageDecoder());
	}

	private <T extends TixPackage> T passThroughChannel(T message) {
		assertTrue(embeddedChannel.writeOutbound(message));
		Object o = embeddedChannel.readOutbound();
		assertNotNull(o);
		DatagramPacket datagramPacket = new DatagramPacket((ByteBuf)o, InetSocketAddress.createUnresolved("127.0.0.1", 1025));
		assertTrue(embeddedChannel.writeInbound(datagramPacket));
		Object returnedMessage = embeddedChannel.readInbound();
		assertNotNull(returnedMessage);
		return (T)returnedMessage;
	}

	@Test
	public void shouldEncodeAndDecodeTixTimestampPackage() throws Exception {
		TixTimestampPackage timestampPackage = new TixTimestampPackage(TixTimeUitl.NANOS_OF_DAY.get(),
				TixTimeUitl.NANOS_OF_DAY.get());
		TixTimestampPackage returnedTimestampPackage = passThroughChannel(timestampPackage);
		assertFalse(timestampPackage == returnedTimestampPackage);
		assertEquals(timestampPackage, returnedTimestampPackage);
	}

	@Test
	public void shouldEncodeAndDecodeTixDataPackage() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(512);
		KeyPair keyPair = generator.genKeyPair();
		String publicKey = new String(Base64.getEncoder().encode(keyPair.getPublic().getEncoded()));
		String instalationName = "test01";
		String filename = "a";
		String message = "a";
		Signature signer = Signature.getInstance("SHA1WithRSA");
		signer.initSign(keyPair.getPrivate());
		signer.update(message.getBytes());
		String signature = new String(signer.sign());
		TixDataPackage dataPackage = new TixDataPackage(TixTimeUitl.NANOS_OF_DAY.get(), TixTimeUitl.NANOS_OF_DAY.get(),
				publicKey, signature, filename, message);
		TixDataPackage returnedDataPackage = passThroughChannel(dataPackage);
		assertFalse(dataPackage == returnedDataPackage);
		assertEquals(dataPackage, returnedDataPackage);
	}
}
