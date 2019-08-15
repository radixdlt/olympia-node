package com.radixdlt.tempo;

import com.radixdlt.common.AID;
import org.radix.time.TemporalProof;

import java.util.Collection;

public interface ConflictDecider {
	AID decide(Collection<TemporalProof> temporalProofs);
}
