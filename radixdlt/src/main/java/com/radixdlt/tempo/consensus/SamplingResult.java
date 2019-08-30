package com.radixdlt.tempo.consensus;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import org.radix.time.TemporalProof;

public final class SamplingResult {
	private final ImmutableMap<AID, TemporalProof> retrievedSamples;
	private final ImmutableSet<EUID> unresponsivePeers;

	public SamplingResult(ImmutableMap<AID, TemporalProof> retrievedSamples, ImmutableSet<EUID> unresponsivePeers) {
		this.retrievedSamples = retrievedSamples;
		this.unresponsivePeers = unresponsivePeers;
	}

	public ImmutableMap<AID, TemporalProof> getRetrievedSamples() {
		return retrievedSamples;
	}

	public ImmutableSet<EUID> getUnresponsivePeers() {
		return unresponsivePeers;
	}
}
