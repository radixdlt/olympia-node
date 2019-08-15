package com.radixdlt.tempo.state;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoException;
import com.radixdlt.tempo.TempoState;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class SampleCollectorState implements TempoState {
	private final Map<EUID, SamplingRequest> requests;

	public SampleCollectorState(Map<EUID, SamplingRequest> requests) {
		this.requests = requests;
	}

	public boolean isRequested(EUID tag, EUID nid, AID aid) {
		SamplingRequest request = requests.get(tag);
		return request != null && request.requestedAids.contains(aid) && request.nids.contains(nid);
	}

	public boolean isPendingDelivery(EUID tag, EUID nid, AID aid) {
		SamplingRequest request = requests.get(tag);
		return request != null && request.isPendingDelivery(nid, aid);
	}

	public Stream<SamplingRequest> completedRequests() {
		return requests.values().stream()
			.filter(SamplingRequest::isComplete);
	}

	public static SampleCollectorState empty() {
		return new SampleCollectorState(ImmutableMap.of());
	}

	public SampleCollectorState withPending(SamplingRequest request) {
		Map<EUID, SamplingRequest> nextRequests = new HashMap<>(requests);
		if (requests.containsKey(request.getTag())) {
			throw new TempoException("Already sampling for tag '" + request.getTag() + "'");
		}
		nextRequests.put(request.getTag(), request);
		return new SampleCollectorState(Collections.unmodifiableMap(nextRequests));
	}

	public SampleCollectorState complete(EUID tag, Set<AID> completedAids, EUID peerNid) {
		Map<EUID, SamplingRequest> nextRequests = new HashMap<>(requests);
		SamplingRequest request = requests.get(tag);
		if (request != null) {
			nextRequests.put(tag, request.complete(completedAids, peerNid));
		}
		return new SampleCollectorState(nextRequests);
	}

	public SampleCollectorState complete(EUID tag) {
		Map<EUID, SamplingRequest> nextRequests = new HashMap<>(requests);
		nextRequests.remove(tag);
		return new SampleCollectorState(nextRequests);
	}

	@Override
	public Object getDebugRepresentation() {
		return ImmutableMap.of(
			"requests", requests.values().stream()
				.map(request -> ImmutableMap.of(
					"tag", request.tag,
					"requestedAids", request.requestedAids,
					"nids", request.nids,
					"pending", request.pending
				))
		);
	}

	public static final class SamplingRequest {
		private final EUID tag;
		private final Set<AID> requestedAids;
		private final Set<EUID> nids;
		private final Map<EUID, Set<AID>> pending;

		private SamplingRequest(EUID tag, Set<AID> requestedAids, Set<EUID> nids, Map<EUID, Set<AID>> pending) {
			this.tag = tag;
			this.requestedAids = requestedAids;
			this.nids = nids;
			this.pending = pending;
		}

		boolean isComplete() {
			return pending.values().stream().allMatch(Set::isEmpty);
		}

		boolean isPendingDelivery(EUID nid, AID aid) {
			Set<AID> pendingForNid = this.pending.get(nid);
			return pendingForNid != null && pendingForNid.contains(aid);
		}

		SamplingRequest complete(Set<AID> aids, EUID nid) {
			if (!pending.containsKey(nid) || requestedAids.stream().noneMatch(aids::contains)) {
				return this;
			}
			Set<AID> nextPendingForNid = new HashSet<>(pending.get(nid));
			nextPendingForNid.removeAll(aids);
			Map<EUID, Set<AID>> nextPending = new HashMap<>(pending);
			nextPending.put(nid, nextPendingForNid);
			return new SamplingRequest(
				tag,
				requestedAids,
				nids,
				nextPending
			);
		}

		public Set<AID> getRequestedAids() {
			return requestedAids;
		}

		public EUID getTag() {
			return tag;
		}

		public static SamplingRequest from(EUID tag, Set<AID> requestedAids, Set<EUID> nids) {
			Map<EUID, Set<AID>> pending = new HashMap<>();
			nids.forEach(nid -> pending.put(nid, requestedAids));
			return new SamplingRequest(tag, requestedAids, nids, pending);
		}
	}
}
