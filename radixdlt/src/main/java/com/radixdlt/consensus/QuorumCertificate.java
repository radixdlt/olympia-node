package com.radixdlt.consensus;

import java.util.Objects;

public final class QuorumCertificate {
	private final Vote vote;

	public QuorumCertificate(Vote vote) {
		this.vote = Objects.requireNonNull(vote);
	}

	public long getRound() {
		return vote.getRound();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof QuorumCertificate)) {
			return false;
		}

		QuorumCertificate qc = (QuorumCertificate) o;
		return Objects.equals(qc.vote, this.vote);
	}

	@Override
	public int hashCode() {
		return vote.hashCode();
	}
}
