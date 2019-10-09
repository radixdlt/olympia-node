package com.radixdlt.tempo.consensus;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class Samples {
	private final Map<EUID, Sample> samplesByPeer;
	private final Set<EUID> unresponsivePeers;

	// derived / computed properties
	private final Map<AID, Integer> preferenceCountsByAid;
	private final AID topPreference;
	private final int topPreferenceCount;

	Samples(Map<EUID, Sample> samplesByPeer, Set<EUID> unresponsivePeers) {
		this.samplesByPeer = Objects.requireNonNull(samplesByPeer);
		this.unresponsivePeers = Objects.requireNonNull(unresponsivePeers);

		this.preferenceCountsByAid = computePreferenceCountsByAid();
		if (!this.preferenceCountsByAid.isEmpty()) {
			// TODO what if there are multiple top preferences with the same number of votes?
			// Multiple preferences with same number of votes should not matter since none of them can have a majority
			Map.Entry<AID, Integer> topPreferenceAndCount = this.preferenceCountsByAid.entrySet().stream()
				.max(Comparator.comparingLong(Map.Entry::getValue))
				.orElseThrow(() -> new IllegalStateException("Could not find max in non-empty preferences"));
			topPreference = topPreferenceAndCount.getKey();
			topPreferenceCount = topPreferenceAndCount.getValue();
		} else {
			topPreference = null;
			topPreferenceCount = 0;
		}
	}

	public Set<EUID> getPeersFor(AID aid) {
		ImmutableSet.Builder<EUID> peers = ImmutableSet.builder();
		for (EUID peer : samplesByPeer.keySet()) {
			if (samplesByPeer.get(peer).uniquePreferences().anyMatch(aid::equals)) {
				peers.add(peer);
			}
		}
		return peers.build();
	}

	int getSamplePeerCount() {
		return unresponsivePeers.size() + samplesByPeer.size();
	}

	boolean hasTopPreference() {
		return topPreference != null;
	}

	AID getTopPreference() {
		return topPreference;
	}

	int getTopPreferenceCount() {
		return topPreferenceCount;
	}

	public Map<EUID, Sample> getSamplesByPeer() {
		return samplesByPeer;
	}

	public Set<EUID> getUnresponsivePeers() {
		return unresponsivePeers;
	}

	private Map<AID, Integer> computePreferenceCountsByAid() {
		Map<AID, Integer> preferenceCounts = new HashMap<>();
		samplesByPeer.keySet().forEach(peerNid -> samplesByPeer.get(peerNid).preferences()
			.forEach(aid -> preferenceCounts.put(aid, preferenceCounts.getOrDefault(aid, 0) + 1)));
		return preferenceCounts;
	}

	@Override
	public String toString() {
		return "Samples{" +
			"samples=" + samplesByPeer +
			", unresponsive=" + unresponsivePeers +
			'}';
	}
}
