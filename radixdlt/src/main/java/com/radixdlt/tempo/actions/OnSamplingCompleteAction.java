package com.radixdlt.tempo.actions;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAction;
import org.radix.time.TemporalProof;

import java.util.Collection;
import java.util.stream.Stream;

public class OnSamplingCompleteAction implements TempoAction {
	private final Collection<TemporalProof> collectedSamples;
	private final Collection<TemporalProof> localSamples;
	private final EUID tag;

	public OnSamplingCompleteAction(Collection<TemporalProof> collectedSamples, ImmutableSet<TemporalProof> localSamples, EUID tag) {
		this.collectedSamples = collectedSamples;
		this.localSamples = localSamples;
		this.tag = tag;
	}

	public Collection<TemporalProof> getCollectedSamples() {
		return collectedSamples;
	}

	public Collection<TemporalProof> getLocalSamples() {
		return localSamples;
	}

	public Collection<TemporalProof> getAllSamples() {
		return Stream.concat(collectedSamples.stream(), localSamples.stream())
				.collect(ImmutableSet.toImmutableSet());
	}

	public EUID getTag() {
		return tag;
	}
}
