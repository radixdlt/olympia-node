package com.radixdlt.tempo.store;

import com.radixdlt.common.AID;
import com.radixdlt.tempo.Resource;

import java.util.Set;

public interface ConsensusStore extends Resource {
	int getConfidenceOrDefault(AID aid, int defaultValue);

	void setConfidence(AID aid, int confidence);

	boolean delete(AID aid);

	void commit(AID aid);

	boolean isCommitted(AID aid);

	boolean isPending(AID aid);

	Set<AID> getPending();
}
