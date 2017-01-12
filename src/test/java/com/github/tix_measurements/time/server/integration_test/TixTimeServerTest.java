package com.github.tix_measurements.time.server.integration_test;

import com.github.tix_measurements.time.core.data.TixDataPacket;
import com.github.tix_measurements.time.core.data.TixPacket;
import com.github.tix_measurements.time.core.data.TixPacketType;
import com.github.tix_measurements.time.core.util.TixCoreUtils;
import com.github.tix_measurements.time.server.TixTimeServer;
import com.github.tix_measurements.time.server.util.jackson.TixPacketSerDe;
import com.github.tix_measurements.time.server.utils.TestDataUtils;
import com.rabbitmq.client.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

public class TixTimeServerTest {

	private String queueHost;
	private String queueName;
	private int serverWorkerThreads;
	private int serverPort;

	private TixTimeTestClient client;

	private TixTimeServer server;

	@Before
	public void setup() throws IOException, TimeoutException {
		queueHost = "localhost";
		queueName = "test-queue-" + RandomStringUtils.randomAlphanumeric(4);
		serverWorkerThreads = Runtime.getRuntime().availableProcessors();
		serverPort = RandomUtils.nextInt(1025, (Short.MAX_VALUE * 2) - 1);
//		setupQueue();
		this.server = new TixTimeServer(queueHost, queueName, serverWorkerThreads, serverPort);
		this.client = new TixTimeTestClient(serverPort);
	}

	@After
	public void teardown() throws IOException, TimeoutException {
		Channel channel = getChannel();
		channel.queueDelete(queueName);
	}

	private Channel getChannel() throws IOException, TimeoutException {
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(queueHost);
		return factory.newConnection()
					.createChannel();
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

	@Test
	public void testSendDataPacket() throws InterruptedException, IOException, TimeoutException {
		byte[] message = TestDataUtils.INSTANCE.generateMessage();
		TixPacket dataPacket = new TixDataPacket(this.client.getClientAddress(), this.client.getServerAddress(),
				TixCoreUtils.NANOS_OF_DAY.get(), TestDataUtils.INSTANCE.getPublicKey(), message,
				TestDataUtils.INSTANCE.getSignature(message));
		testPacket(dataPacket);
		Channel channel = getChannel();
		Consumer consumer = new DefaultConsumer(channel) {
			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
				TixPacketSerDe serde = new TixPacketSerDe();
				TixDataPacket receivedPacket = serde.deserialize(body);
				assertThat(receivedPacket).isEqualTo(dataPacket);
			}
		};
		channel.basicConsume(queueName, true, consumer);
	}
}
