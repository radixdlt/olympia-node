package com.radixdlt.store.tree;

import java.io.OutputStream;
import java.util.HashMap;

public class PMTPath {

	enum Subtree {
		OLD,
		NEW,
		BOTH,
		NONE
	}

	private PMTKey commonPrefix;
	private HashMap<Subtree, PMTKey> rem = new HashMap<>();
	private Subtree suffix;

	public PMTKey getRemainder(Subtree subtree) {
		switch (subtree) {
			case NEW:
			case OLD:
				return rem.get(subtree);
			default:
				throw new IllegalArgumentException("There can be only NEW or OLD remainder");
		}
	}

	public PMTKey getCommonPrefix() {
		return commonPrefix;
	}

	// INFO: Branch has empty key and remainder
	public PMTPath(PMTKey current, PMTKey incoming, PMTKey common) {

		this.rem.put(Subtree.OLD, current);
		this.rem.put(Subtree.NEW, incoming);
		this.commonPrefix = common;
		recogniseRemainder(current, incoming);
	}

	public PMTPath recogniseRemainder(PMTKey current, PMTKey incoming) {
		if (current.isEmpty()) {
			if (incoming.isEmpty()) {
				this.suffix = Subtree.NONE;
			} else {
				this.suffix = Subtree.NEW;
			}
		} else {
			if (incoming.isEmpty()) {
				this.suffix = Subtree.OLD;
			} else {
				this.suffix = Subtree.BOTH;
			}
		}
		return this;
	}

	public Subtree whichRemainderIsLeft() {
		return this.suffix;
	}



	public static PMTPath findCommonPath(PMTKey current, PMTKey incoming) {
		int[] currentNibs =  current.getKey();
		int[] incomingNibs = incoming.getKey();

		var shorter = Math.min(currentNibs.length, incomingNibs.length);

		// TODO: * rewrite into streams to avoid two loops
		//       * doesn't work with primitive ints?
		//       * maybe move to PMTKey?
		var commonLength = 0;
		for (int i=0; i < shorter; i++) {
			if (currentNibs[i] == incomingNibs[i]) {
				commonLength+=1;
			} else {
				break;
			}
		}

		int[] commonElements;
		if (commonLength > 0) {
			commonElements = new int[commonLength];
			for (int i=0; i < commonLength; i++) {
				commonElements[i] = currentNibs[i];
			}
		} else {
			commonElements = new int[0];
		}

		int[] currentRem;
		if (commonLength < currentNibs.length) {
			currentRem = new int[currentNibs.length - commonLength];
			for (int i=0; i < currentRem.length; i++) {
				currentRem[i] = currentNibs[commonLength+i];
			}
		} else {
			currentRem = new int[0];
		}

		int[] incomingRem;
		if (commonLength < incomingNibs.length) {
			incomingRem = new int[incomingNibs.length - commonLength];
			for (int i=0; i < incomingRem.length; i++) {
				incomingRem[i] = incomingNibs[commonLength+i];
			}
		} else {
			incomingRem = new int[0];
		}

		return new PMTPath(new PMTKey(currentRem), new PMTKey(incomingRem), new PMTKey(commonElements));
	}

	public static int[] intoNibbles(byte[] bytes) {
		var nibs = new int[bytes.length*2];
		var nibsIndex = 0;
		for(byte b : bytes) {
			int high = (b & 0xf0) >> 4;
			int low = b & 0xf;
			nibs[nibsIndex] = high;
			nibs[nibsIndex+1] = low;
			nibsIndex=nibsIndex+2;
		}
		return nibs;
	}
}
