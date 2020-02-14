package com.radixdlt.consensus;

import com.radixdlt.common.Atom;
import java.util.Objects;

/**
 * Vertex in the BFT Chain
 */
public final class Vertex {
	private final long round;
	private final Atom atom;

	public Vertex(long round, Atom atom) {
		if (round < 0) {
			throw new IllegalArgumentException("round must be >= 0 but was " + round);
		}
		this.round = round;
		this.atom = atom;
	}

	public long getRound() {
		return round;
	}

	public Atom getAtom() {
		return atom;
	}

	@Override
	public int hashCode() {
		return Objects.hash(round, atom);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Vertex)) {
			return false;
		}

		Vertex v = (Vertex) o;
		return v.round == round && Objects.equals(v.atom, this.atom);
	}
}
