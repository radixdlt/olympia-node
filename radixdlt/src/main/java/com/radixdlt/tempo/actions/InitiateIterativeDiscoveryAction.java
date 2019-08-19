package com.radixdlt.tempo.actions;

import com.radixdlt.tempo.reactive.TempoAction;
import com.radixdlt.tempo.store.CommitmentBatch;
import org.radix.network.peers.Peer;

import java.util.Objects;
import java.util.Optional;

// TODO separate into initiateiterativediscovery (with initial commitments) and reinitiate (without initial commitment)
public class InitiateIterativeDiscoveryAction implements TempoAction {
	private final CommitmentBatch initialCommitments;
	private final Peer peer;

	public InitiateIterativeDiscoveryAction(Peer peer) {
		this(peer, null);
	}

	public InitiateIterativeDiscoveryAction(Peer peer, CommitmentBatch initialCommitments) {
		this.initialCommitments = initialCommitments;
		this.peer = Objects.requireNonNull(peer, "peer is required");
	}

	public Optional<CommitmentBatch> getInitialCommitments() {
		return Optional.ofNullable(initialCommitments);
	}

	public Peer getPeer() {
		return peer;
	}
}
