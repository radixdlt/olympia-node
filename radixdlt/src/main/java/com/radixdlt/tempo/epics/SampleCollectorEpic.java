package com.radixdlt.tempo.epics;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.TempoEpic;
import com.radixdlt.tempo.TempoState;
import com.radixdlt.tempo.TempoStateBundle;
import com.radixdlt.tempo.actions.AcceptAtomAction;
import com.radixdlt.tempo.actions.OnSampleDeliveryFailedAction;
import com.radixdlt.tempo.actions.ReceiveSamplingResultAction;
import com.radixdlt.tempo.actions.RequestSamplingAction;
import com.radixdlt.tempo.actions.TimeoutSampleRequestsAction;
import com.radixdlt.tempo.actions.messaging.ReceiveSampleRequestAction;
import com.radixdlt.tempo.actions.messaging.ReceiveSampleResponseAction;
import com.radixdlt.tempo.actions.messaging.SendSampleRequestAction;
import com.radixdlt.tempo.actions.messaging.SendSampleResponseAction;
import com.radixdlt.tempo.state.SampleCollectorState;
import com.radixdlt.tempo.store.SampleStore;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.peers.Peer;
import org.radix.time.TemporalProof;
import org.radix.time.TemporalVertex;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SampleCollectorEpic implements TempoEpic {
	private static final int SAMPLE_REQUEST_TIMEOUT_SECONDS = 5;

	private static final Logger logger = Logging.getLogger("Sampling");

	private final EUID self;
	private final SampleStore sampleStore;

	public SampleCollectorEpic(EUID self, SampleStore sampleStore) {
		this.self = self;
		this.sampleStore = Objects.requireNonNull(sampleStore, "sampleStore is required");
	}

	@Override
	public Set<Class<? extends TempoState>> requiredState() {
		return ImmutableSet.of(
			SampleCollectorState.class,
			SampleCollectorState.class
		);
	}

	@Override
	public Stream<TempoAction> epic(TempoStateBundle bundle, TempoAction action) {
		if (action instanceof AcceptAtomAction) {
			TempoAtom atom = ((AcceptAtomAction) action).getAtom();
			TemporalProof temporalProof = atom.getTemporalProof();

			// store newly received local temporal proof branches
			TemporalVertex ownVertex = temporalProof.getVertexByNID(self);
			if (ownVertex == null) {
				logger.warn("Accepted atom '" + atom.getAID() + " has no vertex by self");
			} else {
				TemporalProof localBranch = temporalProof.getBranch(ownVertex, true);
				sampleStore.addLocal(localBranch);
			}
			return Stream.empty();
		} else if (action instanceof RequestSamplingAction) {
			RequestSamplingAction request = (RequestSamplingAction) action;
			ImmutableSet<AID> allAids = request.getAllAids();
			ImmutableSet<Peer> samplePeers = request.getSamplePeers();

			// early out with only local samples in case no sample peers were given
			if (samplePeers.isEmpty()) {
				logger.warn("No sample peers given for requesting, returning previous samples (local and collected)");
				return Stream.of(toResult(request.getAllAids(), request.getTag()));
			}

			logger.info("Requesting sampling of '" + allAids + "' from '" + samplePeers + "'");
			// request samples of all aids from all selected sample peers
			Stream<TempoAction> requests = samplePeers.stream()
				.map(peer -> new SendSampleRequestAction(allAids, peer));
			Stream<TempoAction> timeout = Stream.of(new TimeoutSampleRequestsAction(allAids, samplePeers, request.getTag())
				.delay(SAMPLE_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
			return Stream.concat(requests, timeout);
		} else if (action instanceof ReceiveSampleRequestAction) {
			ReceiveSampleRequestAction request = (ReceiveSampleRequestAction) action;
			ImmutableSet.Builder<AID> unavailableAids = ImmutableSet.builder();
			ImmutableSet.Builder<TemporalProof> samples = ImmutableSet.builder();

			// get local samples for all requested aids
			for (AID aid : request.getAids()) {
				Optional<TemporalProof> localSample = sampleStore.getLocal(aid);
				if (localSample.isPresent()) {
					samples.add(localSample.get());
				} else {
					unavailableAids.add(aid);
				}
			}
			if (logger.hasLevel(Logging.DEBUG)) {
				logger.debug("Responding to sample request from " + request.getPeer() + " for " + request.getAids());
			}
			return Stream.of(new SendSampleResponseAction(samples.build(), unavailableAids.build(), request.getPeer()));
		} else if (action instanceof ReceiveSampleResponseAction) {
			ReceiveSampleResponseAction response = (ReceiveSampleResponseAction) action;
			SampleCollectorState collectorState = bundle.get(SampleCollectorState.class);

			if (logger.hasLevel(Logging.DEBUG)) {
				logger.debug(String.format("Received sample response with %s from %s (%d unavailable: %s)",
					response.getTemporalProofs().stream()
						.map(TemporalProof::getAID)
						.collect(Collectors.toList()),
					response.getPeer(), response.getUnavailableAids().size(), response.getUnavailableAids()));
			}
			// add collected samples to store
			response.getTemporalProofs().forEach(sampleStore::addCollected);
			// collect and return the resulting samples
			return collectorState.completedRequests().map(this::toResult);
		} else if (action instanceof TimeoutSampleRequestsAction) {
			TimeoutSampleRequestsAction timeout = (TimeoutSampleRequestsAction) action;
			SampleCollectorState collectorState = bundle.get(SampleCollectorState.class);

			// after timeout, detect any missing samples
			Stream<OnSampleDeliveryFailedAction> failures = timeout.getPeers().stream()
				.map(peer -> new OnSampleDeliveryFailedAction(timeout.getAids().stream()
					.filter(aid -> collectorState.isPendingDelivery(timeout.getTag(), peer.getSystem().getNID(), aid))
					.collect(ImmutableSet.toImmutableSet()), peer))
				.filter(failure -> !failure.getAids().isEmpty());
			// collect and return the resulting samples
			Stream<TempoAction> completions = collectorState.completedRequests().map(this::toResult);
			return Stream.concat(failures, completions);
		}

		return Stream.empty();
	}

	private TempoAction toResult(SampleCollectorState.SamplingRequest request) {
		return toResult(request.getRequestedAids(), request.getTag());
	}

	private TempoAction toResult(Set<AID> requestedAids, EUID tag) {
		// collect the samples for any completed request
		ImmutableSet<TemporalProof> collectedSamples = requestedAids.stream()
			.map(sampleStore::getCollected)
			.filter(Optional::isPresent)
			.map(Optional::get)
			.collect(ImmutableSet.toImmutableSet());
		ImmutableSet<TemporalProof> localSamples = requestedAids.stream()
			.map(sampleStore::getLocal)
			.filter(Optional::isPresent)
			.map(Optional::get)
			.collect(ImmutableSet.toImmutableSet());
		return new ReceiveSamplingResultAction(collectedSamples, localSamples, tag);
	}
}
