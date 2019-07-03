package org.radix.atoms;

import java.util.concurrent.TimeUnit;

import org.radix.common.executors.ScheduledExecutable;
import org.radix.modules.Modules;
import org.radix.modules.Service;
import org.radix.modules.exceptions.ModuleException;
import org.radix.shards.ShardSpace;
import org.radix.universe.system.LocalSystem;
import org.radix.utils.SystemMetaData;

public class GlobalAtomsProfiler extends Service
{
	@Override
	public void start_impl() throws ModuleException
	{
		Modules.ifAvailable(SystemMetaData.class, a -> {
			a.put("ledger.network.processing", 0L);
			a.put("ledger.network.storing", 0L);
			a.put("ledger.network.stored", 0L);
		});

		scheduleWithFixedDelay(new ScheduledExecutable(10, 1, TimeUnit.SECONDS)
		{
			long stored = 0;
			long storing = 0;
			long processing = 0;

			@Override
			public void execute()
			{
				this.stored = 0;
				this.storing = 0;
				this.processing = 0;

				long multiplier = ShardSpace.SHARD_CHUNK_RANGE / LocalSystem.getInstance().getShards().getRange().getSpan();
				Modules.ifAvailable(SystemMetaData.class, a -> {
					this.processing = a.get("ledger.processing", 0l) * multiplier;
					this.storing = a.get("ledger.storing", 0l) * multiplier;
					this.stored = a.get("ledger.stored", 0l) * multiplier;

					a.put("ledger.network.processing", this.processing);
					a.put("ledger.network.storing", this.storing);
					a.put("ledger.network.stored", this.stored);
				});

			}
		});
	}

	@Override
	public void stop_impl() throws ModuleException
	{
	}
}
