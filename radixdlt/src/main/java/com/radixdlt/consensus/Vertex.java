package com.radixdlt.consensus;

import com.radixdlt.common.Atom;

/**
 * Vertex in the BFT Chain graph
 */
public final class Vertex {
	private final long round;
	private final Atom atom;

	public Vertex(long round, Atom atom) {
		this.round = round;
		this.atom = atom;
	}

	public long getRound() {
		return round;
	}

	public Atom getAtom() {
		return atom;
	}
}
