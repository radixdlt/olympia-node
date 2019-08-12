package com.radixdlt.tempo;

import com.radixdlt.common.EUID;
import org.radix.time.TemporalProof;

import java.util.List;

public interface Attestor {
	// TODO separate into vertex data generation, vertex mapper and signer
	TemporalProof attestTo(TemporalProof temporalProof, List<EUID> edges);
}
