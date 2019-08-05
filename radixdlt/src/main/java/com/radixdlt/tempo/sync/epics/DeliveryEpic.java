package com.radixdlt.tempo.sync.epics;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.tempo.AtomStoreView;
import com.radixdlt.tempo.sync.SyncAction;
import com.radixdlt.tempo.sync.SyncEpic;
import com.radixdlt.tempo.sync.TempoAtomSynchroniser.ScheduledDispatcher;
import com.radixdlt.tempo.sync.actions.FailedDeliveryAction;
import com.radixdlt.tempo.sync.actions.ReceiveAtomAction;
import com.radixdlt.tempo.sync.actions.ReceiveDeliveryRequestAction;
import com.radixdlt.tempo.sync.actions.ReceiveDeliveryResponseAction;
import com.radixdlt.tempo.sync.actions.RequestDeliveryAction;
import com.radixdlt.tempo.sync.actions.SendDeliveryRequestAction;
import com.radixdlt.tempo.sync.actions.SendDeliveryResponseAction;
import com.radixdlt.tempo.sync.actions.TimeoutDeliveryRequestAction;
import org.radix.atoms.Atom;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.peers.Peer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class DeliveryEpic implements SyncEpic {
	private static final int DELIVERY_REQUEST_TIMEOUT_SECONDS = 5;

	private final Logger logger = Logging.getLogger("Sync Delivery");
	private final AtomStoreView store;
	private final ScheduledDispatcher scheduledDispatcher;
	private final Set<AID> ongoingDeliveries = Collections.synchronizedSet(new HashSet<>());

	public DeliveryEpic(AtomStoreView store, ScheduledDispatcher scheduledDispatcher) {
		this.store = store;
		this.scheduledDispatcher = scheduledDispatcher;
	}

	@Override
	public Stream<SyncAction> epic(SyncAction action) {
		if (action instanceof ReceiveDeliveryRequestAction) {
			// collect atoms for delivery request
			ImmutableList<AID> requestedAids = ((ReceiveDeliveryRequestAction) action).getAids();
			List<Atom> deliveries = new ArrayList<>();
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

			// if a subset of deliveries is still missing, request a delivery
			if (!missingAids.isEmpty()) {
				SendDeliveryRequestAction sendAction = new SendDeliveryRequestAction(missingAids, request.getPeer());
				ongoingDeliveries.addAll(missingAids);

				// schedule timeout after which deliveries will be checked
				TimeoutDeliveryRequestAction timeoutAction = new TimeoutDeliveryRequestAction(sendAction.getAids(), sendAction.getPeer());
				scheduledDispatcher.schedule(timeoutAction, DELIVERY_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
				return Stream.of(sendAction);
			}
		} else if (action instanceof TimeoutDeliveryRequestAction) {
			// once the timeout has elapsed, check if the deliveries were received
			TimeoutDeliveryRequestAction timeout = (TimeoutDeliveryRequestAction) action;
			ImmutableList<AID> missingAids = timeout.getAids().stream()
				.filter(aid -> !store.contains(aid))
				.collect(ImmutableList.toImmutableList());

			// if the deliveries weren't received, raise a failed delivery action for the requestor
			if (!missingAids.isEmpty()) {
				// TODO where to handle recovery / re-requesting?
				return Stream.of(new FailedDeliveryAction(missingAids, timeout.getPeer()));
			}
		}

		return Stream.empty();
	}
}
