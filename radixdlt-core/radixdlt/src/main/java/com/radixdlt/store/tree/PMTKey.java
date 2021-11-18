package com.radixdlt.store.tree;

import java.util.Arrays;

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
			var tail = new PMTKey(Arrays.copyOfRange(key, 1, key.length)); // TODO: perf, mem?
			tailNibbles = tail;
			return tail;
		} else {
			return firstNibble;
		}
	}

    public Boolean isEmpty() {
		return this.key.length == 0;
	}

	public Boolean isNibble() {
		return this.key.length == 1;
	}

	public byte[] getKey() {
		return this.key;
	}
}
