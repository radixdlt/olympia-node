package com.radixdlt.tempo.epics;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.SampleSelector;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.TempoEpic;
import com.radixdlt.tempo.TempoState;
import com.radixdlt.tempo.TempoStateBundle;
import com.radixdlt.tempo.actions.AcceptAtomAction;
import com.radixdlt.tempo.actions.OnSampleDeliveryFailedAction;
import com.radixdlt.tempo.actions.RequestCollectSamplesAction;
import com.radixdlt.tempo.actions.TimeoutSampleRequestAction;
import com.radixdlt.tempo.actions.messaging.ReceiveSampleRequestAction;
import com.radixdlt.tempo.actions.messaging.ReceiveSampleResponseAction;
import com.radixdlt.tempo.actions.messaging.SendSampleRequestAction;
import com.radixdlt.tempo.actions.messaging.SendSampleResponseAction;
import com.radixdlt.tempo.state.LivePeersState;
import com.radixdlt.tempo.state.SampleCollectorState;
import com.radixdlt.tempo.store.SampleStore;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.time.TemporalProof;
import org.radix.time.TemporalVertex;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class SampleCollectorEpic implements TempoEpic {
	private static final Logger logger = Logging.getLogger("Sampling");

	private final EUID self;
	private final SampleStore sampleStore;
	private final SampleSelector sampleSelector;

	public SampleCollectorEpic(EUID self, SampleStore sampleStore, SampleSelector sampleSelector) {
		this.self = self;
		this.sampleStore = Objects.requireNonNull(sampleStore, "sampleStore is required");
		this.sampleSelector = Objects.requireNonNull(sampleSelector, "sampleSelector is required");
	}

	@Override
	public Set<Class<? extends TempoState>> requiredState() {
		return ImmutableSet.of(
			LivePeersState.class
		);
	}

	@Override
	public Stream<TempoAction> epic(TempoStateBundle bundle, TempoAction action) {
		if (action instanceof AcceptAtomAction) {
			TempoAtom atom = ((AcceptAtomAction) action).getAtom();
			TemporalProof temporalProof = atom.getTemporalProof();
			TemporalVertex ownVertex = temporalProof.getVertexByNID(self);
			if (ownVertex == null) {
				logger.warn("Accepted atom '" + atom.getAID() + " has no vertex by self");
			} else {
				TemporalProof localBranch = temporalProof.getBranch(ownVertex, true);
				sampleStore.addLocal(localBranch);
			}
		} else if (action instanceof RequestCollectSamplesAction) {
			LivePeersState livePeers = bundle.get(LivePeersState.class);
			RequestCollectSamplesAction request = (RequestCollectSamplesAction) action;
			ImmutableSet<AID> allAids = request.getAllAids();
			List<EUID> sampleNids = sampleSelector.selectSamples(livePeers.getNids(), request.getAtom());
			logger.info("Requesting sampling of " + allAids + " from " + sampleNids);
			return sampleNids.stream()
				.map(livePeers::getPeer)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.flatMap(peer -> Stream.of(
					new SendSampleRequestAction(allAids, peer),
					new TimeoutSampleRequestAction(allAids, peer)
				));
		} else if (action instanceof ReceiveSampleRequestAction) {
			ReceiveSampleRequestAction request = (ReceiveSampleRequestAction) action;
			ImmutableSet.Builder<AID> missingAids = ImmutableSet.builder();
			ImmutableSet.Builder<TemporalProof> samples = ImmutableSet.builder();
			for (AID aid : request.getAids()) {
				Optional<TemporalProof> sample = sampleStore.getLocal(aid);
				if (sample.isPresent()) {
					samples.add(sample.get());
				} else {
					missingAids.add(aid);
				}
			}
			return Stream.of(new SendSampleResponseAction(samples.build(), missingAids.build(), request.getPeer()));
		} else if (action instanceof ReceiveSampleResponseAction) {
			ReceiveSampleResponseAction response = (ReceiveSampleResponseAction) action;
			SampleCollectorState sampleCollectorState = bundle.get(SampleCollectorState.class);


		} else if (action instanceof TimeoutSampleRequestAction) {
			TimeoutSampleRequestAction timeout = (TimeoutSampleRequestAction) action;
			EUID peerNid = timeout.getPeer().getSystem().getNID();
			SampleCollectorState sampleCollectorState = bundle.get(SampleCollectorState.class);
			ImmutableSet<AID> missingSamples = timeout.getAids().stream()
				.filter(aid -> sampleCollectorState.isPendingDelivery(aid, peerNid))
				.collect(ImmutableSet.toImmutableSet());
			if (!missingSamples.isEmpty()) {
				return Stream.of(new OnSampleDeliveryFailedAction(missingSamples, timeout.getPeer()));
			}
		}

		return Stream.empty();
	}
}
