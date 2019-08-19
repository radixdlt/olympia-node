package com.radixdlt.tempo.actions;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.reactive.TempoAction;
import org.radix.network.peers.Peer;

public class RequestSamplingAction implements TempoAction {
	private final ImmutableSet<Peer> samplePeers;
	private final ImmutableSet<AID> allAids;
	private final EUID tag;

	public RequestSamplingAction(ImmutableSet<Peer> samplePeers, ImmutableSet<AID> allAids, EUID tag) {
		this.samplePeers = samplePeers;
		this.allAids = allAids;
		this.tag = tag;
	}

	public ImmutableSet<Peer> getSamplePeers() {
		return samplePeers;
	}

	public ImmutableSet<AID> getAllAids() {
		return allAids;
	}

	public EUID getTag() {
		return tag;
	}
}
