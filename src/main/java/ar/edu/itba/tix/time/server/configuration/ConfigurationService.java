package ar.edu.itba.tix.time.server.configuration;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.EnvironmentConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.net.URL;
import java.nio.file.NoSuchFileException;
import java.util.HashMap;
import java.util.Map;

public enum ConfigurationService {
	INSTANCE;

	public static final String DEFAULT_CONFIGURATION_FILE = "configurations.properties";
	public static final String DEV_CONFIGURATION_FILE = "configurations-dev.properties";
	public static final String TEST_CONFIGURATION_FILE = "configurations-test.properties";
	public static final String STAGING_CONFIGURATION_FILE = "configurations-staging.properties";
	public static final String PRODUCTION_CONFIGURATION_FILE = "configurations-production.properties";

	private final Map<String, String> configurationsMap = new HashMap<String, String>() {{
		put("default", DEFAULT_CONFIGURATION_FILE);
		put("", DEFAULT_CONFIGURATION_FILE);
		put(null, DEFAULT_CONFIGURATION_FILE);
		put("dev", DEV_CONFIGURATION_FILE);
		put("test", TEST_CONFIGURATION_FILE);
		put("staging", STAGING_CONFIGURATION_FILE);
		put("production", PRODUCTION_CONFIGURATION_FILE);
	}};

	private int workerThreadsQuantity;
	private String queueHost;
	private String queueName;
	private int port;

	private ConfigurationService() {
		initConfigurations(configurationsMap.get("default"));  // Init configuration with defaults
		Configuration environmentConfig = new EnvironmentConfiguration();
		String environment = environmentConfig.getString("ENVIRONMENT_PROFILE").trim();
		String configFile = configurationsMap.get(environment);
		initConfigurations(configFile);
	}

	private void initConfigurations(String configFile) {
		Configurations configs = new Configurations();
		try {
			URL resource = this.getClass().getClassLoader().getResource(configFile);
			if (resource == null) { throw new NoSuchFileException(configFile); }
			Configuration config = configs.properties(resource.getFile());
			if (config.containsKey("worker-threads-quantity")) {
				workerThreadsQuantity = config.getInt("worker-threads-quantity");
			} else {
				workerThreadsQuantity = Runtime.getRuntime().availableProcessors() * 2;
			}
			queueHost = config.getString("queue-host");
			queueName = config.getString("queue-name");
			port = config.getInt("port");
		} catch (ConfigurationException | NoSuchFileException e) {
			e.printStackTrace();
		}
	}

	public int workerThreadsQuantity() {
		return workerThreadsQuantity;
	}

	public String queueHost() {
		return queueHost;
	}

	public String queueName() {
		return queueName;
	}

	public int port() {
		return port;
	}
}
