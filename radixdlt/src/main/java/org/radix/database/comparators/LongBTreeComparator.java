package org.radix.database.comparators;

import java.util.Comparator;

import com.radixdlt.utils.Longs;

public class LongBTreeComparator implements Comparator<byte[]>
{
	public LongBTreeComparator()
	{}

	@Override
	public int compare(byte[] arg0, byte[] arg1)
	{
		long long0 = Longs.fromByteArray(arg0);
		long long1 = Longs.fromByteArray(arg1);

		if (long0 < long1)
			return -1;

		if (long0 > long1)
			return 1;

		return 0;
	}
}
