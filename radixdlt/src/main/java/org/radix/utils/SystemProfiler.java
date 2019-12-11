package org.radix.utils;

import com.radixdlt.utils.RadixConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.json.JSONObject;
import org.radix.common.executors.Executor;
import org.radix.common.executors.ScheduledExecutable;
import org.radix.database.DatabaseEnvironment;
import org.radix.database.DatabaseStore;
import org.radix.database.exceptions.DatabaseException;
import org.radix.modules.Modules;
import org.radix.modules.exceptions.ModuleException;
import org.radix.modules.exceptions.ModuleResetException;
import org.radix.properties.RuntimeProperties;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;

public class SystemProfiler extends DatabaseStore
{
	// Use classloader to ensure thread safety
	private static class SystemProfilerHolder {
		static final SystemProfiler INSTANCE = new SystemProfiler();
	}

	public static SystemProfiler getInstance() {
		return SystemProfilerHolder.INSTANCE;
	}

	public class ProfilerRecord implements Comparable<ProfilerRecord> {
		public short GET_VERSION() {
			return 100;
		}

		private final String     name;
		private final AtomicLong iterations = new AtomicLong(0l);
		private final AtomicLong duration = new AtomicLong(0l);
		private double average = 0.0;

		public ProfilerRecord() {
			this.name = "NO NAME";
		}

		public ProfilerRecord(String name) {
			this.name = name;
		}

		public AtomicLong getIterations() {
			return iterations;
		}

		public AtomicLong getDuration() {
			return duration;
		}

		public double getAverage() {
			return average;
		}

		public String getName() {
			return name;
		}

		@Override
		public String toString() {
			return String.format("name: '%s' iterations: %s duration: %s average: %s", this.name, this.iterations, this.duration, this.average);
		}

		@Override
		public int compareTo(ProfilerRecord o)
		{
			if (duration.longValue() > o.duration.longValue())
				return -1;
			else if (duration.longValue() < o.duration.longValue())
				return 1;

			return 0;
		}
	}

	private boolean profilerEnabled = false;
	protected final ConcurrentHashMap<String, ProfilerRecord> profilerRecords = new ConcurrentHashMap<>();

	private Database systemProfilerDB = null;

	private SystemProfiler() {
		super(1);
	}

	@Override
	public void start_impl() {
		profilerRecords.clear();
		profilerEnabled = Modules.get(RuntimeProperties.class).get("debug.profiler", false);
		if (profilerEnabled) {
			DatabaseConfig config = new DatabaseConfig();
			config.setAllowCreate(true);

			systemProfilerDB = Modules.get(DatabaseEnvironment.class).getEnvironment().openDatabase(null, "system_profiler", config);

			super.start_impl();

			// Set up flush task //
			ScheduledExecutable flushExecutable = new ScheduledExecutable(10, 10, TimeUnit.SECONDS) {
				@Override
				public void execute() {
					try {
						flushCounterWrites();
					} catch (Exception e) {
						log.error("Unable to complete profiler flush", e);
					}
				}
			};
			Executor.getInstance().scheduleWithFixedDelay(flushExecutable);
		}
	}

	@Override
	public void reset_impl() {
		if (profilerEnabled) {
			Transaction transaction = null;
			try
			{
				transaction = Modules.get(DatabaseEnvironment.class).getEnvironment().beginTransaction(null, new TransactionConfig().setReadUncommitted(true));
				Modules.get(DatabaseEnvironment.class).getEnvironment().truncateDatabase(transaction, "system_profiler", false);
				transaction.commit();
			}
			catch (DatabaseNotFoundException dsnfex)
			{
				if (transaction != null)
					transaction.abort();

				log.warn(dsnfex.getMessage());
			}
			catch (Exception ex)
			{
				if (transaction != null)
					transaction.abort();

				throw new RuntimeException("while resetting database", ex);
			}
		}
	}

	@Override
	public void stop_impl() {
		super.stop_impl();

		if (profilerEnabled) {
			systemProfilerDB.close();
		}
	}

	@Override
	public void build() throws DatabaseException {
		// Nothing to do here.
	}

	@Override
	public void maintenence() throws DatabaseException {
		// Nothing to do here.
	}

	@Override
	public void integrity() throws DatabaseException {
		// Nothing to do here.
	}

	@Override
	public void flush() throws DatabaseException {
		// Nothing to do here.
	}

	public double getAverage(String name) {
		ProfilerRecord sob = profilerRecords.get(name);
		return sob == null ? 0.0 : sob.average;
	}

	private void flushCounterWrites() {
		long start = SystemProfiler.getInstance().begin();
		try {
			for (ProfilerRecord p : profilerRecords.values()) {
				String name = p.getName();
				JSONObject profiler = new JSONObject()
					.put("name", name)
					.put("iterations", p.iterations.get())
					.put("duration", p.duration.get() / 1000000000.0)
					.put("average", p.average/1000000000.0);

				systemProfilerDB.put(null,
					new DatabaseEntry(name.getBytes(RadixConstants.STANDARD_CHARSET)),
					new DatabaseEntry(profiler.toString().getBytes(RadixConstants.STANDARD_CHARSET)));
			}
		} catch (Exception e) {
			log.error("Unable to complete profiler flush", e);
		} finally {
			increment("PROFILER_FLUSH", System.nanoTime() - start);
		}
	}

	public void increment(String name, long duration) {
		if (profilerEnabled) {
			doIncrement(name, 1, duration);
		}
	}

	public void incrementFrom(String name, long startTime) {
		if (profilerEnabled) {
			doIncrement(name, 1, System.nanoTime() - startTime);
		}
	}

	public void incrementFrom(String name, long addition, long startTime) {
		if (profilerEnabled) {
			doIncrement(name, addition, System.nanoTime() - startTime);
		}
	}

	private void doIncrement(String name, long addition, long duration) {
		ProfilerRecord sob = profilerRecords.computeIfAbsent(name, ProfilerRecord::new);

		long iterations = sob.iterations.addAndGet(addition);
		long durations = sob.duration.addAndGet(duration);

		sob.average = ((double) durations) / iterations;
	}

	public long begin() {
		if (profilerEnabled) {
			return System.nanoTime();
		} else {
			return Long.MIN_VALUE;
		}
	}

	@Override
	public String toString() {
		return profilerRecords.toString();
	}

	public List<ProfilerRecord> getAll() {
		return new ArrayList<>(profilerRecords.values());
	}

	public int size() {
		return profilerRecords.size();
	}
}