package com.radixdlt.tempo.store;

import com.radixdlt.common.AID;
import com.radixdlt.tempo.Resource;

import java.util.Set;

public interface ConsensusStore extends Resource {
	int increaseConfidence(AID aid);

	void resetConfidence(AID aid);

	boolean delete(AID aid);

	void commit(AID aid);

	boolean isCommitted(AID aid);

	boolean isUncommitted(AID aid);

	Set<AID> getUncommitted();
}
