package com.radixdlt.tempo.consensus;

import com.radixdlt.common.EUID;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class Samples {
	private final Map<EUID, Sample> samplesByPeer;
	private final Set<EUID> unresponsivePeers;

	public Samples(Map<EUID, Sample> samplesByPeer, Set<EUID> unresponsivePeers) {
		this.samplesByPeer = Objects.requireNonNull(samplesByPeer);
		this.unresponsivePeers = Objects.requireNonNull(unresponsivePeers);
	}

	public Map<EUID, Sample> getSamplesByPeer() {
		return samplesByPeer;
	}

	public Set<EUID> getUnresponsivePeers() {
		return unresponsivePeers;
	}

	@Override
	public String toString() {
		return "Samples{" +
			"samples=" + samplesByPeer +
			", unresponsive=" + unresponsivePeers +
			'}';
	}
}
