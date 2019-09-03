package com.radixdlt.tempo.consensus;

import org.radix.network2.addressbook.Peer;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public final class Samples {
	private final Map<Peer, Sample> samplesByPeer;
	private final Set<Peer> unresponsivePeers;

	public Samples(Map<Peer, Sample> samplesByPeer, Set<Peer> unresponsivePeers) {
		this.samplesByPeer = samplesByPeer;
		this.unresponsivePeers = unresponsivePeers;
	}

	public Collection<Sample> getSamples() {
		return samplesByPeer.values();
	}

	public Map<Peer, Sample> getSamplesByPeer() {
		return samplesByPeer;
	}

	public Set<Peer> getUnresponsivePeers() {
		return unresponsivePeers;
	}
}
