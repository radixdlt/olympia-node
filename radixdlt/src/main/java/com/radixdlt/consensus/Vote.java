package com.radixdlt.consensus;

import java.util.Objects;

/**
 * Represents a vote on a vertex
 */
public final class Vote {
	private final Vertex vertex;

	public Vote(Vertex vertex) {
		this.vertex = Objects.requireNonNull(vertex);
	}

	public Vertex getVertex() {
		return vertex;
	}


	@Override
	public int hashCode() {
		return Objects.hash(vertex);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Vote)) {
			return false;
		}

		Vote v = (Vote) o;
		return Objects.equals(v.vertex, this.vertex);
	}
}
