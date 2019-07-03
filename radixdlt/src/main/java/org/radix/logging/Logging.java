package org.radix.logging;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import com.google.common.collect.Lists;

public class Logging
{
	public static final int OFF		= 0;
	public static final int TRACE 	= 1;
	public static final int INFO	= 2;
	public static final int WARN	= 4;
	public static final int ERROR	= 8;
	public static final int FATAL	= 16;
	public static final int DEBUG	= 32;
	public static final int ALL 	= 255;

	private static Logging logging;

	static
	{
		logging = new Logging();
	}

	public static Logging getInstance()
	{
		return logging;
	}

	public static Logger getLogger()
	{
		return getInstance().get("general");
	}

	public static Logger getLogger(String name)
	{
		return getInstance().get(name);
	}

	private Properties properties;
	private final Map<String, Logger> loggers = new ConcurrentHashMap<>();

	private final Thread loggingThread;
	private final Object flushingLock = new Object();

	private boolean				stdOut = true;
	private final LinkedBlockingQueue<String> stdOutEntries = new LinkedBlockingQueue<>();

	private Logging()
	{
		boolean propertiesLoaded = false;

		try (InputStream loggerPropertiesInput = new FileInputStream(new File("logger.config"))) {
			properties = new Properties();
			properties.load(loggerPropertiesInput);
			propertiesLoaded = true;
		} catch (IOException ex) {
			try (InputStream loggerPropertiesInput = getClass().getResourceAsStream("/logger.config")) {
				if (loggerPropertiesInput != null) {
					properties = new Properties();
					properties.load(loggerPropertiesInput);
					propertiesLoaded = true;
				}
			} catch (IOException ex2) {
				// Ignore error loading from resource and fall through to default properties case below
			}
		}

		if (!propertiesLoaded)
		{
			properties = new Properties();
			//properties.put("logger.general.file", "./logs/general.log");
			properties.put("logger.general.level", String.valueOf((Logging.INFO|Logging.ERROR|Logging.FATAL|Logging.WARN)));
			properties.put("logger.general.stdout", "1");
		}

		Runnable loggingProcessor = () -> {
			while (true) {
				long start = System.nanoTime();

				Logging.this.flush();

				if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 250) {
					try {
						Thread.sleep(Math.max(1, 250 - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)));
					} catch (InterruptedException e) {
						// Exit if we are interrupted
						Thread.currentThread().interrupt();
						break;
					}
				}
			}
		};

		loggingThread = new Thread(loggingProcessor);
		loggingThread.setDaemon(true);
		loggingThread.setName("Logging Processor");
		loggingThread.start();

		Runtime.getRuntime().addShutdownHook(new Thread(this::terminate));
	}

	private Logger get(String name)
	{
		return loggers.computeIfAbsent(name, nm -> {
			return new Logger(nm,
				"stdout".equals(properties.getProperty("logger."+nm+".file")) ? null : properties.getProperty("logger."+nm+".file", "./logs/"+nm+".log"),
				Integer.parseInt(properties.getProperty("logger."+nm+".level", String.valueOf((Logging.INFO|Logging.DEBUG|Logging.ERROR|Logging.FATAL|Logging.WARN)))),
				Integer.parseInt(properties.getProperty("logger."+nm+".stdout", "1"))==0?false:stdOut);
		});
	}

	void toStdOut(String log)
	{
		this.stdOutEntries.add(log);
	}

	private void terminate() {
		loggingThread.interrupt();
		try {
			loggingThread.join();
		} catch (InterruptedException e) {
			// Re-interrupt.  Not going to handle this here.
			Thread.currentThread().interrupt();
		}
		flush();
	}

	public void flush() {
		synchronized (this.flushingLock) {
			this.loggers.values().forEach(Logger::flush);
			if (!this.stdOutEntries.isEmpty()) {
				ArrayList<String> entries = Lists.newArrayList();
				this.stdOutEntries.drainTo(entries);
				// As this is a logging system, it needs to write to System.out
				entries.forEach(System.out::println); //NOSONAR
			}
		}
	}

	public boolean isStdOut()
	{
		return stdOut;
	}

	public void setStdOut(boolean stdOut)
	{
		this.stdOut = stdOut;

		for (Logger logger : loggers.values())
			logger.setStdOut(stdOut);
	}
}
