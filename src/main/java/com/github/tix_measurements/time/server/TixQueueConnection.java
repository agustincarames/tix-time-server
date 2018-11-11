package com.github.tix_measurements.time.server;

import static java.lang.String.format;

import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class TixQueueConnection {

	private final Logger logger = LogManager.getLogger(this.getClass());
	
	private final String queueHost;
	private final String queueUser;
	private final String queuePassword;
	private final String queueName;
	
	private final Connection queueConnection;
	
	public TixQueueConnection(String queueHost, String queueUser, String queuePassword, String queueName) throws ConnectException, InterruptedException {
		this.queueHost = queueHost;
		this.queueUser = queueUser;
		this.queuePassword = queuePassword;
		this.queueName = queueName;
		this.queueConnection = createQueueConnection();
	}

	private Connection createQueueConnection() throws InterruptedException, ConnectException {
		ConnectionFactory connectionFactory = new ConnectionFactory();
		connectionFactory.setHost(queueHost);
		connectionFactory.setUsername(queueUser);
		connectionFactory.setPassword(queuePassword);
		connectionFactory.setAutomaticRecoveryEnabled(true);
		Connection queueConnection = null;
		int retries = 5;
		long backoff = 20000L;
		while (queueConnection == null && retries > 0) {
			try {
				queueConnection = connectionFactory.newConnection();
			} catch (IOException | TimeoutException e) {
				logger.warn("Error while trying to connect with the queue.", e);
				logger.info(format("Retrying in %d seconds. %d retries left.", backoff / 1000, retries));
				Thread.sleep(backoff);
				retries--;
				backoff *= 1.5;
			}
		}
		if (retries == 0) {
			throw new ConnectException("Could not connect to the RabbitMQ Broker!");
		}
		return queueConnection;
	}
	
	public void close() throws IOException {
		queueConnection.close();
	}

	public String getName() {
		return queueName;
	}

	public Connection getConnection() {
		return queueConnection;
	}
}
