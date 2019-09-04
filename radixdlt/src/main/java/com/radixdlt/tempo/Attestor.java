package com.radixdlt.tempo;

import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import org.radix.time.TemporalProof;

import java.util.List;

public interface Attestor {
	TemporalCommitment attestTo(AID aid);
}
