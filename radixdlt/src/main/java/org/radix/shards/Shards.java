package org.radix.shards;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.radix.modules.Module;
import org.radix.modules.Plugin;
import org.radix.modules.exceptions.ModuleException;

public class Shards extends Plugin
{
	public static int toGroup(long shard, int numGroups)
	{
		long delta = -(Long.MIN_VALUE / (numGroups>>1));
		int group;

		if (shard < 0)
			group = (int) ((Long.MIN_VALUE + shard) / delta);
		else
		{
			group = (int) (shard / delta);
			group += (numGroups>>1);
		}

		return group;
	}

	public static ShardRange fromGroup(int group, int numGroups)
	{
		long delta = -(Long.MIN_VALUE / (numGroups>>1));
		long low = (group * delta) - Long.MIN_VALUE;
		long high = low + (delta-1);

		return new ShardRange(low > high ? high : low, low > high ? low : high);
	}

	@Override
	public List<Class<? extends Module>> getDependsOn()
	{
		List<Class<? extends Module>> dependencies = new ArrayList<>();
		return Collections.unmodifiableList(dependencies);
	}
	
	@Override
	public void start_impl() throws ModuleException
	{ }

	@Override
	public void stop_impl() throws ModuleException
	{ }

	@Override
	public String getName() { return "Shards"; }
}

