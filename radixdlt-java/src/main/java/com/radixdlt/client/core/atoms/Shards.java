package com.radixdlt.client.core.atoms;

import java.util.Collection;

public final class Shards {
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
		return shards.stream().anyMatch(this::contains);
	}

	public boolean contains(long shard) {
		return shard >= low && shard <= high;
	}

	@Override
	public String toString() {
		return "[" + low + ", " + high + "]";
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Shards)) {
			return false;
		}

		Shards s = (Shards) o;
		return s.high == this.high && s.low == this.low;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(high) * 31 + Long.hashCode(low);
	}
}
