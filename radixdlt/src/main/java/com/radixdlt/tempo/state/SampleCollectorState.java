package com.radixdlt.tempo.state;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoState;

import java.util.Map;
import java.util.Set;

public class SampleCollectorState implements TempoState {
	private final Map<AID, Set<EUID>> pendingCollectionsByAid;
	private final Map<EUID, Set<AID>> pendingCollectionsByTag;
	private final Map<AID, Set<EUID>> pendingDeliveries;

	public SampleCollectorState(Map<AID, Set<EUID>> pendingCollectionsByAid,
	                            Map<EUID, Set<AID>> pendingCollectionsByTag,
	                            Map<AID, Set<EUID>> pendingDeliveries) {
		this.pendingCollectionsByAid = pendingCollectionsByAid;
		this.pendingCollectionsByTag = pendingCollectionsByTag;
		this.pendingDeliveries = pendingDeliveries;
	}

	public Map<AID, Set<EUID>> getPendingDeliveries() {
		return pendingDeliveries;
	}

	public Map<EUID, Set<AID>> getPendingCollectionsByTag() {
		return pendingCollectionsByTag;
	}

	public Set<EUID> getPendingTags(AID aid) {
		return pendingCollectionsByAid.get(aid);
	}

	public Set<AID> getPendingAids(EUID tag) {
		return pendingCollectionsByTag.get(tag);
	}

	public boolean isPendingCollection(EUID tag) {
		Set<AID> pending = pendingCollectionsByTag.get(tag);
		return pending != null && !pending.isEmpty();
	}

	public boolean isPendingDelivery(AID aid, EUID nid) {
		Set<EUID> pending = pendingDeliveries.get(aid);
		return pending != null && pending.contains(nid);
	}

	public static SampleCollectorState empty() {
		return new SampleCollectorState(ImmutableMap.of(), ImmutableMap.of(), ImmutableMap.of());
	}
}
