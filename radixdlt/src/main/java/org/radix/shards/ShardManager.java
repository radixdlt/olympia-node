package org.radix.shards;

import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Service;

public class ShardManager extends Service
{
	private static final Logger shardsLog = Logging.getLogger ("shards");

	public ShardManager()
	{
		super();
	}

	@Override
	public void start_impl()
	{
	}

	@Override
	public void stop_impl()
	{
	}

	@Override
	public String getName()
	{
		return "Shard Manager";
	}

	public double getShardDistribution()
	{
		return Long.MAX_VALUE;
	}
}
