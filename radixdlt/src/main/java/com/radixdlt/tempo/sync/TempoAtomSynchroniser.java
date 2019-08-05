package com.radixdlt.tempo.sync;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.AtomSynchroniser;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.sync.actions.ReceiveAtomAction;
import com.radixdlt.tempo.sync.actions.ReceiveDeliveryRequestAction;
import com.radixdlt.tempo.sync.actions.ReceiveDeliveryResponseAction;
import com.radixdlt.tempo.sync.actions.ReceivePushAction;
import com.radixdlt.tempo.sync.actions.SendDeliveryRequestAction;
import com.radixdlt.tempo.sync.actions.SendDeliveryResponseAction;
import com.radixdlt.tempo.sync.actions.SendPushAction;
import com.radixdlt.tempo.sync.actions.SyncAtomAction;
import com.radixdlt.tempo.sync.epics.ActiveSyncEpic;
import com.radixdlt.tempo.sync.epics.MessagingEpic;
import com.radixdlt.tempo.sync.messages.DeliveryRequestMessage;
import com.radixdlt.tempo.sync.messages.DeliveryResponseMessage;
import com.radixdlt.tempo.sync.messages.PushMessage;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.messaging.Messaging;
import org.radix.universe.system.LocalSystem;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TempoAtomSynchroniser implements AtomSynchroniser {
	private static final int FULL_INBOUND_QUEUE_RESCHEDULE_TIME_SECONDS = 1;
	private final Logger logger = Logging.getLogger("Sync");

	private final EdgeSelector edgeSelector;
	private final PeerSupplier peerSupplier;

	private final BlockingQueue<TempoAtom> inboundAtoms;
	private final BlockingQueue<SyncAction> syncActions;
	private final List<SyncEpic> syncEpics;
	private final ScheduledExecutorService executor;

	public TempoAtomSynchroniser(LocalSystem localSystem, Messaging messager, EdgeSelector edgeSelector, PeerSupplier peerSupplier) {
		this.edgeSelector = edgeSelector;
		this.peerSupplier = peerSupplier;

		this.executor = Executors.newScheduledThreadPool(4, r -> new Thread(null, null, "Sync"));
		this.inboundAtoms = new LinkedBlockingQueue<>();
		this.syncActions = new LinkedBlockingQueue<>();
		this.syncEpics = ImmutableList.of(
			this::receive,
			MessagingEpic.builder()
			.messager(messager)
			.addInbound("atom.sync.delivery.request", DeliveryRequestMessage.class, ReceiveDeliveryRequestAction::from)
			.addInbound("atom.sync.delivery.response", DeliveryResponseMessage.class, ReceiveDeliveryResponseAction::from)
			.addInbound("atom.sync.push", PushMessage.class, ReceivePushAction::from)
			.addOutound(SendDeliveryRequestAction.class, SendDeliveryRequestAction::toMessage, SendDeliveryRequestAction::getPeer)
			.addOutound(SendDeliveryResponseAction.class, SendDeliveryResponseAction::toMessage, SendDeliveryResponseAction::getPeer)
			.addOutound(SendPushAction.class, SendPushAction::toMessage, SendPushAction::getPeer)
			.build(this::dispatch),
			ActiveSyncEpic.builder()
			.localSystem(localSystem)
			.peerSupplier(peerSupplier)
			.build()
		);

		Thread syncDaemon = new Thread(() -> this.run(executor));
		syncDaemon.setName("Sync Daemon");
		syncDaemon.setDaemon(true);
		syncDaemon.start();
	}

	private void run(ExecutorService executor) {
		while (true) {
			try {
				SyncAction action = syncActions.take();
				executor.submit(() -> execute(action));
			} catch (InterruptedException e) {
				// exit if interrupted
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	private void execute(SyncAction action) {
		List<SyncAction> nextActions = syncEpics.stream()
			.flatMap(epic -> epic.epic(action))
			.collect(Collectors.toList());
		nextActions.forEach(this::dispatch);
	}

	public interface ScheduledDispatcher {
		void schedule(SyncAction action, long delay, TimeUnit unit);
	}

	public interface ImmediateDispatcher {
		void dispatch(SyncAction action);
	}

	private void schedule(SyncAction action, long delay, TimeUnit unit) {
		this.executor.schedule(() -> execute(action), delay, unit);
	}

	private void dispatch(SyncAction syncAction) {
		if (!this.syncActions.add(syncAction)) {
			// TODO handle full action queue better
			throw new IllegalStateException("Action queue full");
		}
	}

	private Stream<SyncAction> receive(SyncAction action) {
		if (action instanceof ReceiveAtomAction) {
			if (!inboundAtoms.add(((ReceiveAtomAction) action).getAtom())) {
				// reschedule
				schedule(action, FULL_INBOUND_QUEUE_RESCHEDULE_TIME_SECONDS, TimeUnit.SECONDS);
			}
		}

		return Stream.empty();
	}

	@Override
	public TempoAtom receive() throws InterruptedException {
		return this.inboundAtoms.take();
	}

	@Override
	public void clear() {
		// TODO review whether this is correct
		this.inboundAtoms.clear();
	}

	@Override
	public List<EUID> selectEdges(TempoAtom atom) {
		return edgeSelector.selectEdges(peerSupplier.getNids(), atom);
	}

	@Override
	public void synchronise(TempoAtom atom) {
		this.dispatch(new SyncAtomAction(atom));
	}
}
