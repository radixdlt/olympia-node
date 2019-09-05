package com.radixdlt.tempo.store;

import com.radixdlt.common.AID;
import com.radixdlt.tempo.Resource;

// TODO does confidence really need to be a persisted store?
public interface ConfidenceStore extends Resource {
	int increaseConfidence(AID aid);

	boolean delete(AID aid);
}
