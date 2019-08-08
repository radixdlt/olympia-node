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
import com.radixdlt.tempo.sync.actions.ReceiveIterativeRequestAction;
import com.radixdlt.tempo.sync.actions.ReceiveIterativeResponseAction;
import com.radixdlt.tempo.sync.actions.ReceivePushAction;
import com.radixdlt.tempo.sync.actions.RepeatScheduleAction;
import com.radixdlt.tempo.sync.actions.ScheduleAction;
import com.radixdlt.tempo.sync.actions.SendDeliveryRequestAction;
import com.radixdlt.tempo.sync.actions.SendDeliveryResponseAction;
import com.radixdlt.tempo.sync.actions.SendIterativeRequestAction;
import com.radixdlt.tempo.sync.actions.SendIterativeResponseAction;
import com.radixdlt.tempo.sync.actions.SendPushAction;
import com.radixdlt.tempo.sync.actions.SyncAtomAction;
import com.radixdlt.tempo.sync.epics.ActiveSyncEpic;
import com.radixdlt.tempo.sync.epics.DeliveryEpic;
import com.radixdlt.tempo.sync.epics.IterativeSyncEpic;
import com.radixdlt.tempo.sync.epics.MessagingEpic;
import com.radixdlt.tempo.sync.epics.PassivePeersEpic;
import com.radixdlt.tempo.sync.messages.DeliveryRequestMessage;
import com.radixdlt.tempo.sync.messages.DeliveryResponseMessage;
import com.radixdlt.tempo.sync.messages.IterativeRequestMessage;
import com.radixdlt.tempo.sync.messages.IterativeResponseMessage;
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
	private final int inboundQueueCapacity;
	private final int syncActionsQueueCapacity;

	private final Logger logger = Logging.getLogger("Sync");

	private final AtomStoreView storeView;
	private final EdgeSelector edgeSelector;
	private final PeerSupplier peerSupplier;

	private final BlockingQueue<TempoAtom> inboundAtoms;
	private final BlockingQueue<SyncAction> syncActions;
	private final List<SyncEpic> syncEpics;
	private final ScheduledExecutorService executor;

	private TempoAtomSynchroniser(int inboundQueueCapacity,
	                              int syncActionsQueueCapacity,
	                              AtomStoreView storeView,
	                              EdgeSelector edgeSelector,
	                              PeerSupplier peerSupplier,
	                              List<SyncEpic> syncEpics,
	                              List<Function<TempoAtomSynchroniser, SyncEpic>> syncEpicBuilders) {
		this.inboundQueueCapacity = inboundQueueCapacity;
		this.syncActionsQueueCapacity = syncActionsQueueCapacity;
		this.storeView = storeView;
		this.edgeSelector = edgeSelector;
		this.peerSupplier = peerSupplier;

		this.executor = Executors.newScheduledThreadPool(4, runnable -> new Thread(null, runnable, "Sync"));
		this.inboundAtoms = new LinkedBlockingQueue<>(this.inboundQueueCapacity);
		this.syncActions = new LinkedBlockingQueue<>(this.syncActionsQueueCapacity);
		this.syncEpics = ImmutableList.<SyncEpic>builder()
			.addAll(syncEpics)
			.addAll(syncEpicBuilders.stream()
				.map(epicBuilder -> epicBuilder.apply(this))
				.collect(Collectors.toList()))
			.add(this::internalEpic)
			.build();

		this.syncEpics.stream()
			.flatMap(SyncEpic::initialActions)
			.forEach(this::dispatch);
	}

	private void run() {
		while (true) {
			try {
				SyncAction action = syncActions.take();
				this.executor.execute(() -> this.execute(action));
			} catch (InterruptedException e) {
				// exit if interrupted
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	private void execute(SyncAction action) {
		if (logger.hasLevel(Logging.DEBUG)) {
			logger.debug("Executing " + action.getClass().getSimpleName());
		}

		List<SyncAction> nextActions = syncEpics.stream()
			.flatMap(epic -> {
				try {
					return epic.epic(action);
				} catch (Exception e) {
					logger.error(String.format("Error while executing %s in %s: '%s'",
						action.getClass().getSimpleName(), epic.getClass().getSimpleName(), e.toString()), e);
					return Stream.empty();
				}
			})
			.collect(Collectors.toList());
		nextActions.forEach(this::dispatch);
	}

	public interface ImmediateDispatcher {
		void dispatch(SyncAction action);
	}

	private void schedule(SyncAction action, long delay, TimeUnit unit) {
		// TODO consider cancellation when shutdown/reset
		this.executor.schedule(() -> dispatch(action), delay, unit);
	}

	private void repeatSchedule(SyncAction action, long initialDelay, long recurrentDelay, TimeUnit unit) {
		// TODO consider cancellation when shutdown/reset
		this.executor.scheduleAtFixedRate(() -> dispatch(action), initialDelay, recurrentDelay, unit);
	}

	private void dispatch(SyncAction action) {
		if (!this.syncActions.add(action)) {
			// TODO handle full action queue better
			throw new IllegalStateException("Action queue full");
		}
	}

	private Stream<SyncAction> internalEpic(SyncAction action) {
		if (action instanceof ReceiveAtomAction) {
			// try to add to inbound queue
			TempoAtom atom = ((ReceiveAtomAction) action).getAtom();
			if (!inboundAtoms.add(atom)) {
				// reschedule
				schedule(action, FULL_INBOUND_QUEUE_RESCHEDULE_TIME_SECONDS, TimeUnit.SECONDS);
			}
		} else if (action instanceof ScheduleAction) {
			ScheduleAction schedule = (ScheduleAction) action;
			schedule(schedule.getAction(), schedule.getDelay(), schedule.getUnit());
		} else if (action instanceof RepeatScheduleAction) {
			RepeatScheduleAction schedule = (RepeatScheduleAction) action;
			repeatSchedule(schedule.getAction(), schedule.getInitialDelay(), schedule.getRecurrentDelay(), schedule.getUnit());
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
					"inboundQueue", getQueueSize(),
					"inboundQueueCapacity", TempoAtomSynchroniser.this.inboundQueueCapacity,

					"actionQueue", TempoAtomSynchroniser.this.syncActions.size(),
					"actionQueueCapacity", TempoAtomSynchroniser.this.syncActionsQueueCapacity
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
			.addEpic(
				PassivePeersEpic.builder()
				.peerSupplier(peerSupplier)
				.build()
			)
			.addEpic(
				IterativeSyncEpic.builder()
				.shardSpaceSupplier(localSystem::getShards)
				.storeView(storeView)
				.build()
			)
			.addEpicBuilder(synchroniser ->
				MessagingEpic.builder()
				.messager(messager)
				.addInbound("tempo.sync.delivery.request", DeliveryRequestMessage.class, ReceiveDeliveryRequestAction::from)
				.addOutbound(SendDeliveryRequestAction.class, SendDeliveryRequestAction::toMessage, SendDeliveryRequestAction::getPeer)
				.addInbound("tempo.sync.delivery.response", DeliveryResponseMessage.class, ReceiveDeliveryResponseAction::from)
				.addOutbound(SendDeliveryResponseAction.class, SendDeliveryResponseAction::toMessage, SendDeliveryResponseAction::getPeer)
				.addInbound("tempo.sync.iterative.request", IterativeRequestMessage.class, ReceiveIterativeRequestAction::from)
				.addOutbound(SendIterativeRequestAction.class, SendIterativeRequestAction::toMessage, SendIterativeRequestAction::getPeer)
				.addInbound("tempo.sync.iterative.response", IterativeResponseMessage.class, ReceiveIterativeResponseAction::from)
				.addOutbound(SendIterativeResponseAction.class, SendIterativeResponseAction::toMessage, SendIterativeResponseAction::getPeer)
				.addInbound("tempo.sync.push", PushMessage.class, ReceivePushAction::from)
				.addOutbound(SendPushAction.class, SendPushAction::toMessage, SendPushAction::getPeer)
				.build(synchroniser::dispatch)
			);
	}

	public static class Builder {
		private int inboundQueueCapacity = 1 << 14;
		private int syncActionsQueueCapacity = 1 << 16;
		private AtomStoreView storeView;
		private PeerSupplier peerSupplier;
		private EdgeSelector edgeSelector;
		private final List<SyncEpic> syncEpics = new ArrayList<>();
		private final List<Function<TempoAtomSynchroniser, SyncEpic>> syncEpicBuilders = new ArrayList<>();

		private Builder() {
		}

		public Builder inboundQueueCapacity(int capacity) {
			this.inboundQueueCapacity = capacity;
			return this;
		}

		public Builder syncActionQueueCapacity(int capacity) {
			this.syncActionsQueueCapacity = capacity;
			return this;
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
				inboundQueueCapacity,
				syncActionsQueueCapacity,
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
