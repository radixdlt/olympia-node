package com.radixdlt.consensus;

public final class QuorumCertificate {
	private final Vote vote;
	public QuorumCertificate(Vote vote) {
		this.vote = vote;
	}

	public long getRound() {
		return vote.getRound();
	}
}
