package com.radixdlt.client.core.atoms;

import java.util.Collection;

public final class Shards {
	private final long low;
	private final long high;

	private Shards(long low, long high) {
		this.low = low;
		this.high = high;
	}

	public static Shards range(long low, long high) {
		return new Shards(low, high);
	}

	/**
	 * Returns whether any of the given shards are in the wrapped shard range
	 *
	 * @param shards collection of shards to check
	 * @return true if there exists a shard in the given collection in the wrapped shard range, false otherwise
	 */
	public boolean intersects(Collection<Long> shards) {
		return shards.stream().anyMatch(this::contains);
	}

	/**
	 * Returns whether the given shard is within the wrapped shard range.
	 *
	 * @param shard the shard to check
	 * @return true if there exists the given shard is in the wrapped shard range, false otherwise
	 */
	public boolean contains(long shard) {
		if (low <= high) {
			return shard >= low && shard <= high;
		} else {
			return shard >= low || shard <= high;
		}
	}

	public long getLow() {
		return this.low;
	}

	public long getHigh() {
		return this.high;
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
