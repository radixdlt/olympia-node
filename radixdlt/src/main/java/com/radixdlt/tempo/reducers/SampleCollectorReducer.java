package com.radixdlt.tempo.reducers;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoReducer;
import com.radixdlt.tempo.TempoState;
import com.radixdlt.tempo.TempoStateBundle;
import com.radixdlt.tempo.actions.OnSampleDeliveryFailedAction;
import com.radixdlt.tempo.actions.ReceiveSamplingResultAction;
import com.radixdlt.tempo.actions.RequestSamplingAction;
import com.radixdlt.tempo.actions.messaging.ReceiveSampleResponseAction;
import com.radixdlt.tempo.actions.messaging.SendSampleRequestAction;
import com.radixdlt.tempo.state.SampleCollectorState;
import com.radixdlt.tempo.state.SampleCollectorState.SamplingRequest;
import org.radix.time.TemporalProof;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SampleCollectorReducer implements TempoReducer<SampleCollectorState> {
	@Override
	public Class<SampleCollectorState> stateClass() {
		return SampleCollectorState.class;
	}

	@Override
	public Set<Class<? extends TempoState>> requiredState() {
		return ImmutableSet.of();
	}

	@Override
	public SampleCollectorState initialState() {
		return SampleCollectorState.empty();
	}

	@Override
	public SampleCollectorState reduce(SampleCollectorState prevState, TempoStateBundle bundle, TempoAction action) {
		if (action instanceof RequestSamplingAction) {
			RequestSamplingAction request = (RequestSamplingAction) action;
			ImmutableSet<EUID> peerNids = request.getSamplePeers().stream()
				.map(peer -> peer.getSystem().getNID())
				.collect(ImmutableSet.toImmutableSet());

			// add new pending request upon request
			return prevState.withPending(SamplingRequest.from(request.getTag(), request.getAllAids(), peerNids));
		} if (action instanceof ReceiveSampleResponseAction) {
			ReceiveSampleResponseAction response = (ReceiveSampleResponseAction) action;
			EUID peerNid = response.getPeer().getSystem().getNID();

			// remove ALL received aids from pending (including unavailable)
			Set<AID> completedAids = Stream.concat(response.getUnavailableAids().stream(),
				response.getTemporalProofs().stream().map(TemporalProof::getAID))
				.collect(Collectors.toSet());
			return prevState.complete(completedAids, peerNid);
		} else if (action instanceof OnSampleDeliveryFailedAction) {
			OnSampleDeliveryFailedAction fail = (OnSampleDeliveryFailedAction) action;
			Set<AID> failedAids = fail.getAids();
			EUID peerNid = fail.getPeer().getSystem().getNID();

			// remove failed aids from pending
			return prevState.complete(failedAids, peerNid);
		} else if (action instanceof ReceiveSamplingResultAction) {
			EUID tag = ((ReceiveSamplingResultAction) action).getTag();

			// complete request when the result has been returned
			return prevState.complete(tag);
		}

		return prevState;
	}
}
