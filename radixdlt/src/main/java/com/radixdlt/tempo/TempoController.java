package com.radixdlt.tempo;

import com.google.common.collect.ImmutableList;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.tempo.actions.AcceptAtomAction;
import com.radixdlt.tempo.actions.ReceiveAtomAction;
import com.radixdlt.tempo.actions.ReceiveDeliveryRequestAction;
import com.radixdlt.tempo.actions.ReceiveDeliveryResponseAction;
import com.radixdlt.tempo.actions.ReceiveIterativeRequestAction;
import com.radixdlt.tempo.actions.ReceiveIterativeResponseAction;
import com.radixdlt.tempo.actions.ReceivePushAction;
import com.radixdlt.tempo.actions.RefreshLivePeersAction;
import com.radixdlt.tempo.actions.RepeatScheduleAction;
import com.radixdlt.tempo.actions.ReselectPassivePeersAction;
import com.radixdlt.tempo.actions.ScheduleAction;
import com.radixdlt.tempo.actions.SendDeliveryRequestAction;
import com.radixdlt.tempo.actions.SendDeliveryResponseAction;
import com.radixdlt.tempo.actions.SendIterativeRequestAction;
import com.radixdlt.tempo.actions.SendIterativeResponseAction;
import com.radixdlt.tempo.actions.SendPushAction;
import com.radixdlt.tempo.epics.ActiveSyncEpic;
import com.radixdlt.tempo.epics.DeliveryEpic;
import com.radixdlt.tempo.epics.IterativeSyncEpic;
import com.radixdlt.tempo.epics.MessagingEpic;
import com.radixdlt.tempo.messages.DeliveryRequestMessage;
import com.radixdlt.tempo.messages.DeliveryResponseMessage;
import com.radixdlt.tempo.messages.IterativeRequestMessage;
import com.radixdlt.tempo.messages.IterativeResponseMessage;
import com.radixdlt.tempo.messages.PushMessage;
import com.radixdlt.tempo.peers.PeerSupplier;
import com.radixdlt.tempo.peers.PeerSupplierAdapter;
import com.radixdlt.tempo.reducers.LivePeersReducer;
import com.radixdlt.tempo.reducers.PassivePeersReducer;
import com.radixdlt.tempo.store.IterativeCursorStore;
import org.radix.database.DatabaseEnvironment;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.network.messaging.Messaging;
import org.radix.network.peers.PeerHandler;
import org.radix.properties.RuntimeProperties;
import org.radix.universe.system.LocalSystem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class TempoController {
	private static final Logger logger = Logging.getLogger("Tempo");

	private static final int FULL_INBOUND_QUEUE_RESCHEDULE_TIME_SECONDS = 1;
	private static final int TEMPO_EXECUTOR_POOL_COUNT = 1;

	private final BlockingQueue<TempoAtom> inboundAtoms;
	private final BlockingQueue<TempoAction> actions;
	private final List<TempoReducer> reducers;
	private final List<TempoEpic> epics;
	private final ScheduledExecutorService executor;

	private TempoController(int inboundQueueCapacity,
	                        int actionsQueueCapacity,
	                        List<TempoReducer> reducers,
	                        List<TempoEpic> epics,
	                        List<Function<ImmediateDispatcher, TempoEpic>> epicBuilders,
	                        List<TempoAction> initialActions) {
		this.inboundAtoms = new LinkedBlockingQueue<>(inboundQueueCapacity);
		this.actions = new LinkedBlockingQueue<>(actionsQueueCapacity);

		this.executor = Executors.newScheduledThreadPool(TEMPO_EXECUTOR_POOL_COUNT, runnable -> new Thread(null, runnable, "Tempo"));
		this.reducers = reducers;
		this.epics = ImmutableList.<TempoEpic>builder()
			.addAll(epics)
			// TODO get rid of epicBuilders, change messanger to middleware
			.addAll(epicBuilders.stream()
				.map(epicBuilder -> epicBuilder.apply(this::dispatch))
				.collect(Collectors.toList()))
			.add(this::internalEpic)
			.build();

		// dispatch initial actions
		Stream.concat(initialActions.stream(), this.epics.stream()
			.flatMap(TempoEpic::initialActions))
			.forEach(this::dispatch);

		Thread tempoDaemon = new Thread(this::run);
		tempoDaemon.setName("Tempo Daemon");
		tempoDaemon.setDaemon(true);
		tempoDaemon.start();
	}

	// TODO this is a spartanic approach to reactive streams, should be replaced down the line
	private void run() {
		TempoStateBundleStore stateStore = new TempoStateBundleStore();
		reducers.forEach(reducer -> stateStore.put(reducer.stateClass(), reducer.initialState()));

		while (true) {
			try {
				TempoAction action = actions.take();
				if (logger.hasLevel(Logging.TRACE)) {
					logger.trace("Executing " + action.getClass().getSimpleName());
				}

				for (TempoReducer reducer : reducers) {
					try {
						TempoStateBundle bundle = stateStore.bundleFor(reducer.requiredState());
						TempoState currentState = stateStore.get(reducer.stateClass());
						TempoState nextState = reducer.reduce(currentState, bundle, action);
						stateStore.put(reducer.stateClass(), nextState);
					} catch (Exception e) {
						logger.error(String.format("Error while executing %s in reducer %s: '%s'",
							action.getClass().getSimpleName(), reducer.getClass().getSimpleName(), e.toString()), e);
					}
				}

				List<TempoAction> nextActions = epics.stream()
					.flatMap(epic -> {
						try {
							TempoStateBundle bundle = stateStore.bundleFor(epic.requiredState());
							return epic.epic(bundle, action);
						} catch (Exception e) {
							logger.error(String.format("Error while executing %s in epic %s: '%s'",
								action.getClass().getSimpleName(), epic.getClass().getSimpleName(), e.toString()), e);
							return Stream.empty();
						}
					})
					.collect(Collectors.toList());
				nextActions.forEach(this::dispatch);
			} catch (InterruptedException e) {
				// exit if interrupted
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	public interface ImmediateDispatcher {
		void dispatch(TempoAction action);
	}

	private void delay(TempoAction action, long delay, TimeUnit unit) {
		// TODO consider cancellation when shutdown/reset
		this.executor.schedule(() -> dispatch(action), delay, unit);
	}

	private void repeatSchedule(TempoAction action, long initialDelay, long recurrentDelay, TimeUnit unit) {
		// TODO consider cancellation when shutdown/reset
		this.executor.scheduleAtFixedRate(() -> dispatch(action), initialDelay, recurrentDelay, unit);
	}

	private void dispatch(TempoAction action) {
		if (!this.actions.add(action)) {
			// TODO handle full action queue better
			throw new IllegalStateException("Action queue full");
		}
	}

	private Stream<TempoAction> internalEpic(TempoStateBundle state, TempoAction action) {
		if (action instanceof ReceiveAtomAction) {
			// try to add to inbound queue
			TempoAtom atom = ((ReceiveAtomAction) action).getAtom();
			if (!inboundAtoms.add(atom)) {
				// reschedule
				delay(action, FULL_INBOUND_QUEUE_RESCHEDULE_TIME_SECONDS, TimeUnit.SECONDS);
			}
		} else if (action instanceof ScheduleAction) {
			ScheduleAction schedule = (ScheduleAction) action;
			delay(schedule.getAction(), schedule.getDelay(), schedule.getUnit());
		} else if (action instanceof RepeatScheduleAction) {
			RepeatScheduleAction schedule = (RepeatScheduleAction) action;
			repeatSchedule(schedule.getAction(), schedule.getInitialDelay(), schedule.getRecurrentDelay(), schedule.getUnit());
		}

		return Stream.empty();
	}

	public CompletableFuture<TempoAtom> resolve(TempoAtom atom, Collection<TempoAtom> conflictingAtoms) {
		// TODO hook up local conflict resolver epic
		throw new UnsupportedOperationException("Not yet implemented");
	}

	public void accept(TempoAtom atom) {
		this.dispatch(new AcceptAtomAction(atom));
	}

	public void queue(TempoAtom atom) {
		this.dispatch(new ReceiveAtomAction(atom));
	}

	public TempoAtom receive() throws InterruptedException {
		return this.inboundAtoms.take();
	}

	int getInboundQueueSize() {
		return this.inboundAtoms.size();
	}

	int getActionQueueSize() {
		return this.actions.size();
	}

	// TODO figure out nicer way than reset
	void reset() {
		this.inboundAtoms.clear();
		this.actions.clear();
	}

	private static class TempoStateBundleStore {
		private final Map<Class<? extends TempoState>, TempoState> states;

		private TempoStateBundleStore() {
			this.states = new HashMap<>();
		}

		private void put(Class<? extends TempoState> stateClass, TempoState state) {
			this.states.put(stateClass, state);
		}

		private <T extends TempoState> T get(Class<T> stateClass) {
			return (T) states.get(stateClass);
		}

		TempoStateBundle bundleFor(Set<Class<? extends TempoState>> requiredStates) {
			Map<Class<? extends TempoState>, TempoState> bundledStates = new HashMap<>();
			for (Class<? extends TempoState> requiredState : requiredStates) {
				TempoState state = states.get(requiredState);
				if (state == null) {
					throw new TempoException("Required state '" + requiredState.getSimpleName() + "' is not available");
				} else {
					bundledStates.put(requiredState, state);
				}
			}

			return new TempoStateBundle() {
				@Override
				public <T extends TempoState> T get(Class<T> stateClass) {
					T state = (T) bundledStates.get(stateClass);
					if (state == null) {
						throw new TempoException("Requested state '" + stateClass.getSimpleName() + "' was not required");
					}
					return state;
				}
			};
		}
	}

	public static Builder defaultBuilder(AtomStoreView storeView) {
		LocalSystem localSystem = LocalSystem.getInstance();
		Messaging messager = Messaging.getInstance();
		PeerSupplier peerSupplier = new PeerSupplierAdapter(() -> Modules.get(PeerHandler.class));
		Builder builder = builder()
			.addEpic(DeliveryEpic.builder()
				.storeView(storeView)
				.build())
			.addEpicBuilder(controller -> MessagingEpic.builder()
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
				.build(controller))
			.addReducer(new LivePeersReducer(peerSupplier))
			.addReducer(new PassivePeersReducer(16))
			.addInitialAction(new RefreshLivePeersAction().repeat(10, 5, TimeUnit.SECONDS))
			.addInitialAction(new ReselectPassivePeersAction().repeat(10, 20, TimeUnit.SECONDS));
		if (Modules.get(RuntimeProperties.class).get("tempo2.sync.active", true)) {
			builder.addEpic(ActiveSyncEpic.builder()
				.localSystem(localSystem)
				.build());
		}
		if (Modules.get(RuntimeProperties.class).get("tempo2.sync.iterative", true)) {
			IterativeCursorStore cursorStore = new IterativeCursorStore(
				() -> Modules.get(DatabaseEnvironment.class),
				() -> Modules.get(Serialization.class)
			);
			cursorStore.open();
			builder.addEpic(IterativeSyncEpic.builder()
				.shardSpaceSupplier(localSystem::getShards)
				.storeView(storeView)
				.cursorStore(cursorStore)
				.build());
		}

		return builder;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private int inboundQueueCapacity = 1 << 14;
		private int syncActionsQueueCapacity = 1 << 16;
		private final List<TempoEpic> epics = new ArrayList<>();
		private final List<TempoReducer> reducers = new ArrayList<>();
		private final List<Function<ImmediateDispatcher, TempoEpic>> epicBuilders = new ArrayList<>();
		private final List<TempoAction> initialActions = new ArrayList<>();

		private Builder() {
		}

		public Builder inboundQueueCapacity(int capacity) {
			this.inboundQueueCapacity = capacity;
			return this;
		}

		public Builder actionQueueCapacity(int capacity) {
			this.syncActionsQueueCapacity = capacity;
			return this;
		}

		public Builder addEpic(TempoEpic epic) {
			Objects.requireNonNull(epic, "epic is required");
			this.epics.add(epic);
			return this;
		}

		public Builder addEpicBuilder(Function<ImmediateDispatcher, TempoEpic> epicBuilder) {
			Objects.requireNonNull(epicBuilder, "epicBuilder is required");
			this.epicBuilders.add(epicBuilder);
			return this;
		}

		public Builder addReducer(TempoReducer reducer) {
			Objects.requireNonNull(reducer, "reducer is required");
			this.reducers.add(reducer);
			return this;
		}

		public Builder addInitialAction(TempoAction initialAction) {
			Objects.requireNonNull(initialAction, "initialAction is required");
			this.initialActions.add(initialAction);
			return this;
		}

		public TempoController build() {
			return new TempoController(
				inboundQueueCapacity,
				syncActionsQueueCapacity,
				reducers,
				epics,
				epicBuilders,
				initialActions
			);
		}
	}
}
