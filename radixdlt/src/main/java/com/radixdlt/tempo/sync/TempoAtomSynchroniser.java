package com.radixdlt.tempo.sync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atoms.AtomStatus;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.AtomStoreView;
import com.radixdlt.tempo.AtomSyncView;
import com.radixdlt.tempo.AtomSynchroniser;
import com.radixdlt.tempo.LegacyUtils;
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
import com.radixdlt.tempo.sync.epics.DeliveryEpic;
import com.radixdlt.tempo.sync.epics.MessagingEpic;
import com.radixdlt.tempo.sync.messages.DeliveryRequestMessage;
import com.radixdlt.tempo.sync.messages.DeliveryResponseMessage;
import com.radixdlt.tempo.sync.messages.PushMessage;
import org.radix.atoms.Atom;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.messaging.Messaging;
import org.radix.universe.system.LocalSystem;

import java.util.List;
import java.util.Map;
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
	private static final int INBOUND_QUEUE_CAPACITY = 1 << 14;
	private static final int SYNC_ACTIONS_QUEUE_CAPACITY = 1 << 16;

	private final Logger logger = Logging.getLogger("Sync");

	private final AtomStoreView storeView;
	private final EdgeSelector edgeSelector;
	private final PeerSupplier peerSupplier;

	private final BlockingQueue<TempoAtom> inboundAtoms;
	private final BlockingQueue<SyncAction> syncActions;
	private final List<SyncEpic> syncEpics;
	private final ScheduledExecutorService executor;

	public TempoAtomSynchroniser(AtomStoreView storeView, LocalSystem localSystem, Messaging messager, EdgeSelector edgeSelector, PeerSupplier peerSupplier) {
		this.storeView = storeView;
		this.edgeSelector = edgeSelector;
		this.peerSupplier = peerSupplier;

		this.executor = Executors.newScheduledThreadPool(4, r -> new Thread(null, null, "Sync"));
		this.inboundAtoms = new LinkedBlockingQueue<>(INBOUND_QUEUE_CAPACITY);
		this.syncActions = new LinkedBlockingQueue<>(SYNC_ACTIONS_QUEUE_CAPACITY);
		this.syncEpics = ImmutableList.of(
			this::receive,
			MessagingEpic.builder()
			.messager(messager)
			.addInbound("atom.sync.delivery.request", DeliveryRequestMessage.class, ReceiveDeliveryRequestAction::from)
			.addInbound("atom.sync.delivery.response", DeliveryResponseMessage.class, ReceiveDeliveryResponseAction::from)
			.addInbound("atom.sync.push", PushMessage.class, ReceivePushAction::from)
			.addOutbound(SendDeliveryRequestAction.class, SendDeliveryRequestAction::toMessage, SendDeliveryRequestAction::getPeer)
			.addOutbound(SendDeliveryResponseAction.class, SendDeliveryResponseAction::toMessage, SendDeliveryResponseAction::getPeer)
			.addOutbound(SendPushAction.class, SendPushAction::toMessage, SendPushAction::getPeer)
			.build(this::dispatch),
			ActiveSyncEpic.builder()
			.localSystem(localSystem)
			.peerSupplier(peerSupplier)
			.build(),
			DeliveryEpic.builder()
			.view(storeView)
			.dispatcher(this::schedule)
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
			// try to add to inbound queue
			TempoAtom atom = ((ReceiveAtomAction) action).getAtom();
			if (!inboundAtoms.add(atom)) {
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

	@Override
	public AtomSyncView getLegacyAdapter() {
		return new AtomSyncView() {
			@Override
			public void receive(Atom atom) {
				TempoAtom tempoAtom = LegacyUtils.fromLegacyAtom(atom);
				TempoAtomSynchroniser.this.dispatch(new ReceiveAtomAction(tempoAtom));
			}

			@Override
			public AtomStatus getAtomStatus(AID aid) {
				return TempoAtomSynchroniser.this.storeView.contains(aid) ? AtomStatus.STORED : AtomStatus.DOES_NOT_EXIST;
			}

			@Override
			public long getQueueSize() {
				return TempoAtomSynchroniser.this.inboundAtoms.size();
			}

			@Override
			public Map<String, Object> getMetaData() {
				return ImmutableMap.of(
					"inboundQueue", getQueueSize()
				);
			}
		};
	}
}
