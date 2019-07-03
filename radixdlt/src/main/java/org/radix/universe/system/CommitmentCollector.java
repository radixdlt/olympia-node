package org.radix.universe.system;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.radixdlt.common.Pair;

public class CommitmentCollector
{
	private int length;
	private Map<Long, BitSet> bitSets;

	public CommitmentCollector(int length)
	{
		this.length = length;
		this.bitSets = new HashMap<Long, BitSet>();
	}

	public int length()
	{
		return this.length;
	}

	public int size()
	{
		return this.bitSets.size();
	}

	public int has(byte[] bytes)
	{
		BitSet bitSet = BitSet.valueOf(bytes);

		List<Long> indexes = new ArrayList<Long>(this.bitSets.keySet());
		Collections.sort(indexes);

		int matchedBits = 0;
		int maxMatchedBits = 0;
		long index = indexes.get(0);
		long startIndex = indexes.get(0);

		while (startIndex < indexes.get(indexes.size()-1))
		{
			if (this.bitSets.containsKey(index))
			{
				if (bitSet.get((int) (index - startIndex)) == this.bitSets.get(index).get((int) (index - startIndex)))
				{
					index++;
					matchedBits++;

					if (matchedBits == this.length)
						return matchedBits;
				}
				else
				{
					startIndex++;
					index = startIndex;

					if (matchedBits > maxMatchedBits)
						maxMatchedBits = matchedBits;

					matchedBits = 0;
				}
			}
			else
				index++;

			if (index > indexes.get(indexes.size()-1))
			{
				startIndex++;
				index = startIndex;

				if (matchedBits > maxMatchedBits)
					maxMatchedBits = matchedBits;

				matchedBits = 0;
			}
		}

		return matchedBits;
	}

	public boolean has(byte[] bytes, int threshold)
	{
		BitSet bitSet = BitSet.valueOf(bytes);

		List<Long> indexes = new ArrayList<Long>(this.bitSets.keySet());
		Collections.sort(indexes);

		int matchedBits = 0;
		long index = indexes.get(0);
		long startIndex = indexes.get(0);

		while (startIndex < indexes.get(indexes.size()-1))
		{
			if (this.bitSets.containsKey(index))
			{
				if (bitSet.get((int) (index - startIndex)) == this.bitSets.get(index).get((int) (index - startIndex)))
				{
					index++;
					matchedBits++;

					if (matchedBits == threshold)
						return true;
				}
				else
				{
					startIndex++;
					index = startIndex;
					matchedBits = 0;
				}
			}
			else
				index++;

			if (index > indexes.get(indexes.size()-1))
			{
				startIndex++;
				index = startIndex;
				matchedBits = 0;
			}
		}

		return false;
	}

	public int has(long index, byte[] bytes)
	{
		BitSet bitSet = BitSet.valueOf(bytes);

		int matchedBits = 0;
		long startIndex = index;
		long endIndex = index + this.length;
		while (index < endIndex)
		{
			if (this.bitSets.containsKey(index) == true)
			{
				if (bitSet.get((int) (index - startIndex)) == this.bitSets.get(index).get((int) (index - startIndex)))
				{
					matchedBits++;

					if (matchedBits == this.length)
						break;
				}
			}

			index++;
		}

		return matchedBits;
	}

	public boolean has(long index, byte[] bytes, int threshold)
	{
		BitSet bitSet = BitSet.valueOf(bytes);

		int matchedBits = 0;
		long startIndex = index;
		long endIndex = index + this.length;
		while (index < endIndex)
		{
			if (this.bitSets.containsKey(index) == true)
			{
				if (bitSet.get((int) (index - startIndex)) == this.bitSets.get(index).get((int) (index - startIndex)))
					matchedBits++;
			}

			index++;
		}

		return matchedBits >= threshold;
	}

	public Pair<byte[], Integer> get(long index)
	{
		BitSet bitSet = new BitSet(this.length);

		int knownBits = 0;
		long startIndex = index;
		long endIndex = index + this.length;
		while (index < endIndex)
		{
			if (this.bitSets.containsKey(index) == true)
			{
				bitSet.set((int) (index - startIndex), this.bitSets.get(index).get((int) (index - startIndex)));
				knownBits++;
			}

			index++;
		}

		return new Pair<>(bitSet.toByteArray(), knownBits);
	}

	public void put(long index, byte[] bytes)
	{
		BitSet bitSet = BitSet.valueOf(bytes);

		this.bitSets.put(index, bitSet);
	}

	public byte[] remove(long index)
	{
		BitSet bitSet = this.bitSets.remove(index);

		if (bitSet != null)
		{
			byte[] bytes = bitSet.toByteArray();
			byte[] paddedBytes = new byte[(this.length/8) + ((this.length % 8) > 0 ? 1 : 0)];
			java.lang.System.arraycopy(bytes, 0, paddedBytes, 0, bytes.length);
			return paddedBytes;
		}

		return null;
	}
}
