package org.radix.atoms;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.radix.atoms.events.AtomEvent;
import org.radix.atoms.events.AtomListener;
import org.radix.atoms.events.AtomStoredEvent;
import org.radix.common.executors.ScheduledExecutable;
import org.radix.events.Events;
import org.radix.modules.Modules;
import org.radix.modules.Service;
import org.radix.modules.exceptions.ModuleException;
import org.radix.utils.SystemMetaData;

import com.google.common.util.concurrent.AtomicDouble;

public class LocalAtomsProfiler extends Service implements AtomListener
{
	private static final int PROFILER_DURATION = 10;

	private static class ProfilerSample
	{
		private final long   storing;
		private final double storingPerShard;
		private final long   processed;

		public ProfilerSample(long storing, double storingPerShard, long processed) {
			this.storing = storing;
			this.storingPerShard = storingPerShard;
			this.processed = processed;
		}
	}

	private Map<Integer, ProfilerSample> samples = new LinkedHashMap<Integer, ProfilerSample>(20, 0.75f, false) {
		private static final long serialVersionUID = 1L;

		@Override
		protected boolean removeEldestEntry(Entry<Integer, ProfilerSample> arg0) {
			return this.size() > PROFILER_DURATION;
		}
	};

	private final long   timebase = System.currentTimeMillis(); // Avoid int overflow
	private AtomicLong   storing = new AtomicLong(0);
	private AtomicLong   storingPeak = new AtomicLong(0);
	private AtomicDouble storingPerShard = new AtomicDouble(0);
	private AtomicLong   processed = new AtomicLong(0);

	@Override
	public void start_impl() throws ModuleException
	{
		Modules.ifAvailable(SystemMetaData.class, a -> {
			a.put("ledger.processing", 0L);
			a.put("ledger.storingPerShard", 0L);
			a.put("ledger.storing", 0L);
		});

		scheduleWithFixedDelay(new ScheduledExecutable(1, 1, TimeUnit.SECONDS)
		{
			@Override
			public void execute()
			{
				int period = (int) TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - timebase);

				final ProfilerSample sample = new ProfilerSample(
					LocalAtomsProfiler.this.storing.getAndSet(0L),
					LocalAtomsProfiler.this.storingPerShard.getAndSet(0.0),
					LocalAtomsProfiler.this.processed.getAndSet(0L)
				);

				LocalAtomsProfiler.this.samples.put(period, sample);

				final long totalStoring = LocalAtomsProfiler.this.samples.values().stream().collect(Collectors.summingLong(s -> s.storing)) / PROFILER_DURATION;
				final long totalStoringPerShard = Math.round(LocalAtomsProfiler.this.samples.values().stream().collect(Collectors.summingDouble(s -> s.storingPerShard)) / PROFILER_DURATION);
				final long totalProcessed = LocalAtomsProfiler.this.samples.values().stream().collect(Collectors.summingLong(s -> s.processed)) / PROFILER_DURATION;
				Modules.ifAvailable(SystemMetaData.class, a -> {
					a.put("ledger.storing", totalStoring);
					a.put("ledger.storingPerShard", totalStoringPerShard);
					a.put("ledger.processing", totalProcessed);
				});

				if (totalStoring > LocalAtomsProfiler.this.storingPeak.get()) {
					LocalAtomsProfiler.this.storingPeak.set(totalStoring);
					Modules.ifAvailable(SystemMetaData.class, a -> a.put("ledger.storing.peak", LocalAtomsProfiler.this.storingPeak.get()));
				}
			}
		});

		Events.getInstance().register(AtomEvent.class, this);
	}

	@Override
	public void stop_impl() throws ModuleException
	{
		Events.getInstance().deregister(AtomEvent.class, this);
	}

	@Override
	public void process(AtomEvent event) {
		if (event instanceof AtomStoredEvent) {
			LocalAtomsProfiler.this.storing.incrementAndGet();
			LocalAtomsProfiler.this.storingPerShard.addAndGet(1.0 / event.getAtom().getShards().size());
		}
	}
}
