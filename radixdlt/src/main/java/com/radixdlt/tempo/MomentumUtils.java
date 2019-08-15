package com.radixdlt.tempo;

import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import org.radix.time.TemporalProof;
import org.radix.time.TemporalVertex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class MomentumUtils {
	private MomentumUtils() {
		throw new IllegalStateException("Can't construct");
	}

	public static Map<AID, Long> measure(Map<AID, List<EUID>> preferences, Function<EUID, Long> measure) {
		Map<AID, Long> momenta = new HashMap<>();
		for (Map.Entry<AID, List<EUID>> aidAndNids : preferences.entrySet()) {
			AID aid = aidAndNids.getKey();
			List<EUID> nids = aidAndNids.getValue();
			long momentum = 0L;
			for (EUID nid : nids) {
				momentum += measure.apply(nid);
			}
			momenta.put(aid, momentum);
		}
		return momenta;
	}

	public static Map<AID, List<EUID>> extractPreferences(Collection<TemporalProof> temporalProofs) {
		Map<EUID, AID> latestPreferenceByNid = new HashMap<>();
		Map<EUID, Long> latestClockByNid = new HashMap<>();
		for (TemporalProof temporalProof : temporalProofs) {
			AID aid = temporalProof.getAID();
			for (TemporalVertex vertex : temporalProof.getVertices()) {
				EUID nid = vertex.getOwner().getUID();
				Long latest = latestClockByNid.get(nid);
				long clock = vertex.getClock();
				if (latest == null || latest < clock) {
					latestClockByNid.put(nid, clock);
					latestPreferenceByNid.put(nid, aid);
				}
			}
		}
		Map<AID, List<EUID>> nidsByPreferences = new HashMap<>();
		latestPreferenceByNid.forEach((nid, aid)
			-> nidsByPreferences.computeIfAbsent(aid, x -> new ArrayList<>()).add(nid));
		return nidsByPreferences;
	}
}
