package com.radixdlt.tempo.store;

import com.radixdlt.common.AID;
import com.radixdlt.tempo.Resource;

public interface ConfidenceStore extends Resource {
	int increaseConfidence(AID aid);

	void resetConfidence(AID aid);

	boolean delete(AID aid);
}
