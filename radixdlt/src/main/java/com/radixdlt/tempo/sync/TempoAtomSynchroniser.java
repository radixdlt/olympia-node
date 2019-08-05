package com.radixdlt.tempo.sync;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.AtomSynchroniser;
import com.radixdlt.tempo.sync.actions.DeliveryRequestAction;
import com.radixdlt.tempo.sync.actions.DeliveryResponseAction;
import com.radixdlt.tempo.sync.actions.PushAction;
import com.radixdlt.tempo.sync.messages.DeliveryRequestMessage;
import com.radixdlt.tempo.sync.messages.DeliveryResponseMessage;
import com.radixdlt.tempo.sync.messages.PushMessage;
import org.radix.atoms.Atom;
import org.radix.network.messaging.Messaging;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Collectors;

public class TempoAtomSynchroniser implements AtomSynchroniser {
	private final Messaging messager;
	private final EdgeSelector edgeSelector;
	private final PeerSupplier peerSupplier;

	private final BlockingQueue<SyncAction> syncActions;
	private final List<SyncEpic> syncEpics;

	public TempoAtomSynchroniser(Messaging messager, EdgeSelector edgeSelector, PeerSupplier peerSupplier) {
		this.messager = messager;
		this.edgeSelector = edgeSelector;
		this.peerSupplier = peerSupplier;

		this.syncActions = new LinkedBlockingDeque<>();
		this.syncEpics = ImmutableList.of(
			MessagingEpic.builder()
			.messager(messager)
			.addInbound("atom.sync.delivery.request", DeliveryRequestMessage.class, DeliveryRequestAction::from)
			.addInbound("atom.sync.delivery.response", DeliveryResponseMessage.class, DeliveryResponseAction::from)
			.addInbound("atom.sync.push", PushMessage.class, PushAction::from)
			.addOutound(DeliveryRequestAction.class, DeliveryRequestAction::toMessage, DeliveryRequestAction::getPeer)
			.addOutound(DeliveryResponseAction.class, DeliveryResponseAction::toMessage, DeliveryResponseAction::getPeer)
			.addOutound(PushAction.class, PushAction::toMessage, PushAction::getPeer)
			.build(this::dispatch)
		);

		ExecutorService executor = Executors.newCachedThreadPool();
		Thread syncDaemon = new Thread(() -> this.run(executor));
		syncDaemon.setName("Sync Daemon");
		syncDaemon.setDaemon(true);
		syncDaemon.start();
	}

	private void run(ExecutorService executor) {
		while (true) {
			try {
				SyncAction action = syncActions.take();
				executor.submit(() -> {
					List<SyncAction> nextActions = syncEpics.stream()
						.flatMap(epic -> epic.epic(action))
						.collect(Collectors.toList());
					nextActions.forEach(this::dispatch);
				});
			} catch (InterruptedException e) {
				// exit if interrupted
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	private void dispatch(SyncAction syncAction) {
		if (!this.syncActions.add(syncAction)) {
			// TODO handle queue full better
			throw new RuntimeException("Action queue full");
		}
	}

	@Override
	public Atom receive() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<EUID> selectEdges(Atom atom) {
		return edgeSelector.selectEdges(peerSupplier.getNids(), atom);
	}

	@Override
	public void synchronise(Atom atom) {
		throw new UnsupportedOperationException();
	}
}
