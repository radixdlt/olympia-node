package com.radixdlt.tempo;

import org.radix.time.TemporalProof;

import java.util.Collection;

public interface ConflictDecider {
	TemporalProof decide(Collection<TemporalProof> temporalProofs);
}
