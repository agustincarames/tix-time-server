package com.github.tix_measurements.time.server;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
@Profile("test")
public class TixTimeServerTest {

	@Value("${tix-time-server.port}")
	private int port;

	private TixTimeTestClient client;
	private TestReceiver receiver;

	@Before
	public void setup() {

	}

	@Test
	public void testSendTimestampPacket() {

	}

	@Test
	public void testSendDataPacket() {

	}

	private static class TixTimeTestClient {

	}

	private static class TestReceiver {

	}
}
