package com.radixdlt.tempo.epics;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.tempo.AtomStoreView;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoEpic;
import com.radixdlt.tempo.actions.HandleFailedDeliveryAction;
import com.radixdlt.tempo.actions.ReceiveAtomAction;
import com.radixdlt.tempo.actions.ReceiveDeliveryRequestAction;
import com.radixdlt.tempo.actions.ReceiveDeliveryResponseAction;
import com.radixdlt.tempo.actions.RequestDeliveryAction;
import com.radixdlt.tempo.actions.SendDeliveryRequestAction;
import com.radixdlt.tempo.actions.SendDeliveryResponseAction;
import com.radixdlt.tempo.actions.TimeoutDeliveryRequestAction;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.peers.Peer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class DeliveryEpic implements TempoEpic {
	private static final int DELIVERY_REQUEST_TIMEOUT_SECONDS = 5;
	private static final int DEFER_DELIVERY_REQUEST_TIMEOUT_SECONDS = 5;

	private static final Logger logger = Logging.getLogger("Sync");

	private final AtomStoreView store;
	private final Set<AID> ongoingDeliveries;

	private DeliveryEpic(AtomStoreView store) {
		this.store = store;

		this.ongoingDeliveries = Collections.newSetFromMap(new ConcurrentHashMap<>());
	}

	@Override
	public Stream<TempoAction> epic(TempoAction action) {
		if (action instanceof ReceiveDeliveryRequestAction) {
			// collect atoms for delivery request
			ImmutableList<AID> requestedAids = ((ReceiveDeliveryRequestAction) action).getAids();
			List<TempoAtom> deliveries = new ArrayList<>();
			for (AID requestedAid : requestedAids) {
				store.get(requestedAid).ifPresent(deliveries::add);
			}

			// send collected atoms back to the requesting peer (individually)
			Peer peer = ((ReceiveDeliveryRequestAction) action).getPeer();
			return deliveries.stream()
				.map(atom -> new SendDeliveryResponseAction(atom, peer));
		} else if (action instanceof ReceiveDeliveryResponseAction) {
			ReceiveDeliveryResponseAction response = (ReceiveDeliveryResponseAction) action;
			// remove atom from ongoing deliveries
			ongoingDeliveries.remove(response.getAtom().getAID());

			// forward received atom from delivery response
			return Stream.of(new ReceiveAtomAction(response.getAtom()));
		} else if (action instanceof RequestDeliveryAction) {
			// check if any requested deliveries have not arrived in the meantime
			RequestDeliveryAction request = (RequestDeliveryAction) action;
			ImmutableList<AID> missingAids = request.getAids().stream()
				.filter(aid -> !store.contains(aid))
				.collect(ImmutableList.toImmutableList());

			// if a subset of deliveries is missing, figure out which ones to request and defer
			if (!missingAids.isEmpty()) {
				// TODO potential concurrency problems here?
				ImmutableList<AID> ongoingAids = missingAids.stream()
					.filter(ongoingDeliveries::contains)
					.collect(ImmutableList.toImmutableList());
				ImmutableList<AID> unrequestedAids = missingAids.stream()
					.filter(aid -> !ongoingDeliveries.contains(aid))
					.collect(ImmutableList.toImmutableList());

				// defer already ongoing deliveries until later (in case the ongoing ones fail)
				Stream<TempoAction> deferred;
				if (!ongoingAids.isEmpty()){
					deferred = Stream.of(new RequestDeliveryAction(ongoingAids, request.getPeer())
						.delay(DEFER_DELIVERY_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
				} else {
					deferred = Stream.empty();
				}

				// request delivery of all aids that are not currently being delivered
				Stream<TempoAction> requested;
				if (!unrequestedAids.isEmpty()) {
					SendDeliveryRequestAction sendAction = new SendDeliveryRequestAction(unrequestedAids, request.getPeer());
					ongoingDeliveries.addAll(unrequestedAids);

					logger.info("Requesting delivery of " + unrequestedAids.size() + " aids from " + request.getPeer());
					// schedule timeout after which deliveries will be checked
					TimeoutDeliveryRequestAction timeoutAction = new TimeoutDeliveryRequestAction(sendAction.getAids(), sendAction.getPeer());
					requested = Stream.of(sendAction, timeoutAction.delay(DELIVERY_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
				} else {
					requested = Stream.empty();
				}

				return Stream.concat(deferred, requested);
			}
		} else if (action instanceof TimeoutDeliveryRequestAction) {
			// once the timeout has elapsed, check if the deliveries were received
			TimeoutDeliveryRequestAction timeout = (TimeoutDeliveryRequestAction) action;
			ImmutableList<AID> missingAids = timeout.getAids().stream()
				.filter(ongoingDeliveries::contains)
				.collect(ImmutableList.toImmutableList());

			// if the deliveries weren't received, raise a failed delivery action for the requestor
			if (!missingAids.isEmpty()) {
				// TODO consider re-requesting from the same peer once (add TTL counter to timeout action)
				ongoingDeliveries.removeAll(missingAids);
				logger.warn("Delivery of " + missingAids.size() + " aids from " + timeout.getPeer() + " has timed out");
				// TODO handle / log this somewhere?
				return Stream.of(new HandleFailedDeliveryAction(missingAids, timeout.getPeer()));
			}
		}

		return Stream.empty();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private AtomStoreView storeView;

		private Builder() {
		}

		public Builder storeView(AtomStoreView view) {
			this.storeView = view;
			return this;
		}

		public DeliveryEpic build() {
			Objects.requireNonNull(this.storeView, "storeView is required");

			return new DeliveryEpic(this.storeView);
		}
	}
}
