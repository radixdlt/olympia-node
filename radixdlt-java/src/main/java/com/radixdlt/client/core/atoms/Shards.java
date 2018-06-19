package com.radixdlt.client.core.atoms;

import java.util.Collection;

public class Shards {
	private final long low;
	private final long high;

	private Shards(long low, long high) {
		if (high < low) {
			throw new IllegalArgumentException();
		}

		this.low = low;
		this.high = high;
	}

	public static Shards range(long low, long high) {
		return new Shards(low, high);
	}

	public boolean intersects(Collection<Long> shards) {
		return shards.stream().anyMatch(shard -> shard >= low && shard <= high);
	}

	@Override
	public String toString() {
		return "[" + low + ", " + high + "]";
	}

	@Override
	public boolean equals(Object obj) {
		Shards s = (Shards)obj;
		return s.high == this.high && s.low == this.low;
	}

	@Override
	public int hashCode() {
		//TODO: fix HACK
		return (low + "-" + high).hashCode();
	}
}
