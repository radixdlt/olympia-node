package org.radix.shards;

public class Shards
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
}

