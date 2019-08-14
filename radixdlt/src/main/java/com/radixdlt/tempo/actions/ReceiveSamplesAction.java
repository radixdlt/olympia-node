package com.radixdlt.tempo.actions;

import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAction;
import org.radix.time.TemporalProof;

import java.util.Collection;

public class ReceiveSamplesAction implements TempoAction {
	private final Collection<TemporalProof> samples;
	private final EUID tag;

	public ReceiveSamplesAction(Collection<TemporalProof> samples, EUID tag) {
		this.samples = samples;
		this.tag = tag;
	}

	public Collection<TemporalProof> getSamples() {
		return samples;
	}

	public EUID getTag() {
		return tag;
	}
}
