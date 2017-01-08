package com.github.tix_measurements.time.server.integration_test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tix_measurements.time.core.data.TixDataPacket;
import com.github.tix_measurements.time.core.data.TixPacket;
import com.github.tix_measurements.time.core.data.TixPacketType;
import com.github.tix_measurements.time.core.util.TixCoreUtils;
import com.github.tix_measurements.time.server.TixTimeServer;
import com.github.tix_measurements.time.server.handler.TixUdpServerHandlerTest;
import com.github.tix_measurements.time.server.utils.TestDataUtils;
import com.rabbitmq.client.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.security.KeyPair;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

public class TixTimeServerTest {

	private static String QUEUE_HOST;
	private static String QUEUE_NAME;
	private static int SERVER_WORKER_THREADS;
	private static int SERVER_PORT;

	private TixTimeTestClient client;

	private TixTimeServer server;

	@BeforeClass
	public static void classSetup() throws IOException, TimeoutException {
		QUEUE_HOST = "localhost";
		QUEUE_NAME = "test-queue-" + RandomStringUtils.randomAlphanumeric(4);
		SERVER_WORKER_THREADS = Runtime.getRuntime().availableProcessors();
		SERVER_PORT = RandomUtils.nextInt(1025, (Short.MAX_VALUE * 2) - 1);
		setupQueue();
	}

	private static Channel getChannel() throws IOException, TimeoutException {
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(QUEUE_HOST);
		return factory.newConnection()
					.createChannel();
	}

	private static void setupQueue() throws IOException, TimeoutException {
		Channel channel = getChannel();
		channel.queueDeclare(QUEUE_NAME, false, false, false, null);
	}

	@AfterClass
	public static void classTeardown() throws IOException, TimeoutException {
		Channel channel = getChannel();
		channel.queueDelete(QUEUE_NAME);
	}

	@Before
	public void setup() {
		this.server = new TixTimeServer(QUEUE_HOST, QUEUE_NAME, SERVER_WORKER_THREADS, SERVER_PORT);
		this.client = new TixTimeTestClient(SERVER_PORT);
	}

	private void testPacket(TixPacket packet) throws InterruptedException {
		this.server.start();
		this.client.start(new ChannelInboundHandlerAdapter(){
			@Override
			public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
				TixPacket incomingPacket = (TixPacket)msg;
				assertThat(incomingPacket.getTo()).isEqualTo(packet.getFrom());
				assertThat(incomingPacket.getFrom()).isEqualTo(packet.getTo());
				assertThat(incomingPacket.getInitialTimestamp()).isEqualTo(packet.getInitialTimestamp());
				assertThat(incomingPacket.getReceptionTimestamp()).isNotZero();
				assertThat(incomingPacket.getSentTimestamp()).isNotZero();
				assertThat(incomingPacket.getFinalTimestamp()).isZero();
				ctx.close();
			}
		});
		this.client.send(packet);
		this.client.stop();
		this.server.stop();
	}

	@Test
	public void testSendShortPacket() throws InterruptedException {
		TixPacket packet = new TixPacket(this.client.getClientAddress(), this.client.getServerAddress(),
				TixPacketType.SHORT, TixCoreUtils.NANOS_OF_DAY.get());
		testPacket(packet);
	}

	@Test
	public void testSendLongPacket() throws InterruptedException {
		TixPacket packet = new TixPacket(this.client.getClientAddress(), this.client.getServerAddress(),
				TixPacketType.LONG, TixCoreUtils.NANOS_OF_DAY.get());
		testPacket(packet);
	}
}
