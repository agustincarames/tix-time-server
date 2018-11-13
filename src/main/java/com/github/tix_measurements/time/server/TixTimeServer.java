package com.github.tix_measurements.time.server;

import com.github.fnmartinez.jmicroconfigs.ConfigurationManager;
import com.github.tix_measurements.time.server.data.TixUdpDataService;
import com.github.tix_measurements.time.server.health.TixHealthService;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;

import java.io.IOException;
import java.net.ConnectException;

public class TixTimeServer {

	private final Logger logger = LogManager.getLogger(this.getClass());
	
	private final TixQueueConnection queue;
	private final TixHealthService healthServer;
	private final TixUdpDataService udpDataServer;

	private static void setLogLevel(String logLevel) {
		Level level = Level.getLevel(logLevel);
		LoggerContext ctx = LoggerContext.getContext(false);
		Configuration config = ctx.getConfiguration();
		config.getLoggers().forEach((s, loggerConfig) -> loggerConfig.setLevel(level));
		ctx.updateLoggers();
	}

	public static void main(String[] args) {
		Logger mainLogger = LogManager.getLogger();
		TixTimeServer server = null;
		try {
			ConfigurationManager configs = new ConfigurationManager("TIX");
			configs.loadConfigs();
			server = new TixTimeServer(configs.getString("queue.host"),
					configs.getString("queue.user"),
					configs.getString("queue.password"),
					configs.getString("queue.name"),
					Integer.parseInt(configs.getString("worker-threads-quantity")),
					Integer.parseInt(configs.getString("udp-port")),
					Integer.parseInt(configs.getString("http-port")));
			setLogLevel(configs.getString("log-level"));
			
			server.start();
			System.out.println("Press enter to terminate");
			while(System.in.available() == 0) {
				Thread.sleep(10);
			}
			server.stop();
		} catch (Throwable t) {
			mainLogger.fatal("Unexpected exception", t);
			System.exit(1);
		}
	}

	public TixTimeServer(String queueHost, String queueUser, String queuePassword,
	                     String queueName, int workerThreadsQuantity, int udpPort, int httpPort) throws ConnectException, InterruptedException {
		logger.entry(queueHost, queueUser, queuePassword, queueName, workerThreadsQuantity, udpPort, httpPort);
		
		this.queue = new TixQueueConnection(queueHost, queueUser, queuePassword, queueName);
		this.healthServer = new TixHealthService(httpPort);
		this.udpDataServer = new TixUdpDataService(udpPort, workerThreadsQuantity, queue);
		
		logger.exit(this);
	}

	public void start() {
		logger.info("Starting Server");
		try {
			healthServer.start();
			udpDataServer.start();
		} catch (InterruptedException e) {
			logger.fatal("Interrupted", e);
			logger.catching(e);
			this.stop();
		}
	}

	public void stop() {
		logger.info("Shutting down");
		udpDataServer.stop();
		healthServer.stop();
		try {
			logger.info("Closing queue connection.");
			queue.close();
		} catch (IOException e) {
			logger.catching(e);
			logger.warn("Error while closing queue connection.", e);
		}
		logger.info("Server shutdown");
	}
}
