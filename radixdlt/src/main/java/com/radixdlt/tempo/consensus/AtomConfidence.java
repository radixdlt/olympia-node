package com.radixdlt.tempo.consensus;

import com.radixdlt.common.AID;
import com.radixdlt.tempo.Resource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// TODO does confidence need to be a persisted store?
public final class AtomConfidence {
	private static final Map<AID, Integer> confidenceByAid = new ConcurrentHashMap<>();

	public int increaseConfidence(AID aid) {
		return confidenceByAid.compute(aid, (a, prev) -> {
			if (prev == null) {
				return 1;
			} else {
				return prev + 1;
			}
		});
	}

	public boolean reset(AID aid) {
		return confidenceByAid.remove(aid) != null;
	}
}
