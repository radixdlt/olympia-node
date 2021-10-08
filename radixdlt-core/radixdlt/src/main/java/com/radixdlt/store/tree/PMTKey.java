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
			var first = new PMTKey(TreeUtils.getFirstNibble(key)); // TODO: perf, mem?
			firstNibble = first;
			return first;
		} else {
			return firstNibble;
		}
	}

	public PMTKey getTailNibbles() {
		if (tailNibbles == null) {
			var tail = new PMTKey(Arrays.copyOfRange(key, 4, key.length)); // TODO: perf, mem?
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
		return this.key.length == 4;
	}

	public byte[] toByte() {
		return key;
	}
}
