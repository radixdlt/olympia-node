package com.radixdlt.store.tree;

import java.util.Arrays;
import java.util.Objects;

public class PMTKey {

	private byte[] key;
	private PMTKey firstNibble;
	private PMTKey tailNibbles;

	public PMTKey(byte[] inputKey) {
		this.key = inputKey;
	}

	public PMTKey getFirstNibble() {
		if (firstNibble == null) {
			var first = new PMTKey(new byte[]{key[0]}); // TODO: perf, mem?
			firstNibble = first;
			return first;
		} else {
			return firstNibble;
		}
	}

	public int getFirstNibbleValue() {
		return this.key[0];
	}

	public PMTKey getTailNibbles() {
		if (tailNibbles == null) {
			if (key.length > 1) {
				var tail = new PMTKey(Arrays.copyOfRange(key, 1, key.length)); // TODO: perf, mem?
				tailNibbles = tail;
				return tail;
			} else {
				return new PMTKey(new byte[0]);
			}
		} else {
			return tailNibbles;
		}
	}

    public boolean isEmpty() {
		return this.key.length == 0;
	}

	public Boolean isNibble() {
		return this.key.length == 1;
	}

	public byte[] getKey() {
		return this.key;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		PMTKey pmtKey = (PMTKey) o;
		return Arrays.equals(key, pmtKey.key)
				&& Objects.equals(firstNibble, pmtKey.firstNibble)
				&& Objects.equals(tailNibbles, pmtKey.tailNibbles);
	}

	@Override
	public int hashCode() {
		return Objects.hash(key, firstNibble, tailNibbles);
	}
}
