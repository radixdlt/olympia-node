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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
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

	private TempoAtomSynchroniser(AtomStoreView storeView,
	                              EdgeSelector edgeSelector,
	                              PeerSupplier peerSupplier,
	                              List<SyncEpic> syncEpics,
	                              List<Function<TempoAtomSynchroniser, SyncEpic>> syncEpicBuilders) {
		this.storeView = storeView;
		this.edgeSelector = edgeSelector;
		this.peerSupplier = peerSupplier;

		this.executor = Executors.newScheduledThreadPool(4, r -> new Thread(null, null, "Sync"));
		this.inboundAtoms = new LinkedBlockingQueue<>(INBOUND_QUEUE_CAPACITY);
		this.syncActions = new LinkedBlockingQueue<>(SYNC_ACTIONS_QUEUE_CAPACITY);
		this.syncEpics = ImmutableList.<SyncEpic>builder()
			.addAll(syncEpics)
			.addAll(syncEpicBuilders.stream()
				.map(epicBuilder -> epicBuilder.apply(this))
				.collect(Collectors.toList()))
			.build();
	}

	private void run() {
		while (true) {
			try {
				SyncAction action = syncActions.take();
				if (logger.hasLevel(Logging.DEBUG)) {
					logger.debug(action.toString());
				}

				this.executor.submit(() -> execute(action));
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

	public static Builder builder() {
		return new Builder();
	}

	public static Builder defaultBuilder(AtomStoreView storeView, LocalSystem localSystem, Messaging messager, PeerSupplier peerSupplier) {
		return new Builder()
			.storeView(storeView)
			.peerSupplier(peerSupplier)
			.addEpic(
				ActiveSyncEpic.builder()
				.localSystem(localSystem)
				.peerSupplier(peerSupplier)
				.build())
			.addEpic(
				DeliveryEpic.builder()
				.storeView(storeView)
				.build())
			.addEpicBuilder(synchroniser ->
				MessagingEpic.builder()
				.messager(messager)
				.addInbound("atom.sync.delivery.request", DeliveryRequestMessage.class, ReceiveDeliveryRequestAction::from)
				.addInbound("atom.sync.delivery.response", DeliveryResponseMessage.class, ReceiveDeliveryResponseAction::from)
				.addInbound("atom.sync.push", PushMessage.class, ReceivePushAction::from)
				.addOutbound(SendDeliveryRequestAction.class, SendDeliveryRequestAction::toMessage, SendDeliveryRequestAction::getPeer)
				.addOutbound(SendDeliveryResponseAction.class, SendDeliveryResponseAction::toMessage, SendDeliveryResponseAction::getPeer)
				.addOutbound(SendPushAction.class, SendPushAction::toMessage, SendPushAction::getPeer)
				.build(synchroniser::dispatch))
			.addEpicBuilder(synchroniser -> synchroniser::receive);
	}

	public static class Builder {
		private AtomStoreView storeView;
		private PeerSupplier peerSupplier;
		private EdgeSelector edgeSelector;
		private final List<SyncEpic> syncEpics = new ArrayList<>();
		private final List<Function<TempoAtomSynchroniser, SyncEpic>> syncEpicBuilders = new ArrayList<>();

		private Builder() {
		}

		public Builder addEpic(SyncEpic epic) {
			Objects.requireNonNull(epic, "epic is required");
			this.syncEpics.add(epic);
			return this;
		}

		public Builder addEpicBuilder(Function<TempoAtomSynchroniser, SyncEpic> epicBuilder) {
			this.syncEpicBuilders.add(epicBuilder);
			return this;
		}

		public Builder storeView(AtomStoreView storeView) {
			this.storeView = storeView;
			return this;
		}

		public Builder peerSupplier(PeerSupplier peerSupplier) {
			this.peerSupplier = peerSupplier;
			return this;
		}

		public Builder edgeSelector(EdgeSelector edgeSelector) {
			this.edgeSelector = edgeSelector;
			return this;
		}

		public TempoAtomSynchroniser build() {
			Objects.requireNonNull(storeView, "storeView is required");
			Objects.requireNonNull(peerSupplier, "peerSupplier is required");
			Objects.requireNonNull(edgeSelector, "edgeSelector is required");

			TempoAtomSynchroniser tempoAtomSynchroniser = new TempoAtomSynchroniser(
				storeView,
				edgeSelector,
				peerSupplier,
				syncEpics,
				syncEpicBuilders
			);

			Thread syncDaemon = new Thread(tempoAtomSynchroniser::run);
			syncDaemon.setName("Sync Daemon");
			syncDaemon.setDaemon(true);
			syncDaemon.start();

			return tempoAtomSynchroniser;
		}
	}
}
