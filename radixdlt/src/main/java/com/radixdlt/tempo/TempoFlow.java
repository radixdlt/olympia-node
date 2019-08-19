package com.radixdlt.tempo;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.tempo.TempoException;
import com.radixdlt.tempo.TempoStateBundle;
import com.radixdlt.tempo.TempoStateBundleGenerator;
import com.radixdlt.tempo.reactive.TempoAction;
import com.radixdlt.tempo.reactive.TempoActionWithState;
import com.radixdlt.tempo.reactive.TempoState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A Tempo Flow that can be passed to a reactive stream pipeline and accept actions to pass through generated pipelines
 */
public final class TempoFlow {
	private final Map<Class<? extends TempoAction>, List<StatelessTempoFlowGenerator>> statelessGeneratorsByClass;
	private final Map<Class<? extends TempoAction>, List<StatefulTempoFlowGenerator>> statefulGeneratorsByClass;
	private final int downstreamBufferCapacity;

	TempoFlow(int downstreamBufferCapacity) {
		this.downstreamBufferCapacity = downstreamBufferCapacity;
		this.statelessGeneratorsByClass = new HashMap<>();
		this.statefulGeneratorsByClass = new HashMap<>();
	}

	// TODO make not public?
	void accept(TempoAction action, TempoStateBundleGenerator state) {
		Objects.requireNonNull(action, "action is required");
		Class<? extends TempoAction> actionClass = action.getClass();

		// run stateless generators
		List<StatelessTempoFlowGenerator> statelessGenerators = this.statelessGeneratorsByClass.get(actionClass);
		if (statelessGenerators != null) {
			TempoAction castAction = actionClass.cast(action);
			for (StatelessTempoFlowGenerator generator : statelessGenerators) {
				if (!generator.accept(castAction)) {
					throw new TempoException("Generator for action " + actionClass.getSimpleName() + " cannot accept any more values");
				}
			}
		}

		// run stateless generators
		List<StatefulTempoFlowGenerator> statefulGenerators = this.statefulGeneratorsByClass.get(actionClass);
		if (statefulGenerators != null) {
			TempoAction castAction = actionClass.cast(action);
			for (StatefulTempoFlowGenerator generator : statefulGenerators) {
				TempoStateBundle stateBundle = state.bundleFor(generator.getRequiredState());
				if (!generator.accept(TempoActionWithState.from(castAction, stateBundle))) {
					throw new TempoException("Generator for action " + actionClass.getSimpleName() + " cannot accept any more values");
				}
			}
		}
	}

	/**
	 * Creates a stateless flow of a certain tempo action type
	 * @param actionClass The action class of interest
	 * @param <T> The type of the action class
	 * @return A stream on which the flow is to be built
	 */
	public final <T extends TempoAction> Stream<T> of(Class<T> actionClass) {
		Objects.requireNonNull(actionClass, "actionClass is required");

		StatelessTempoFlowGenerator<T> generator = new StatelessTempoFlowGenerator<>(downstreamBufferCapacity);
		statelessGeneratorsByClass.computeIfAbsent(actionClass, x -> new ArrayList<>()).add(generator);

		return Stream.generate(generator);
	}

	/**
	 * Creates a stateful flow of a certain tempo action type with some required states
	 * @param actionClass The action class of interest
	 * @param requiredState The required state
	 * @param requiredStates An arbitrary array of further required states
	 * @param <T> The type of the action class
	 * @return A stream on which the flow is to be built
	 */
	@SafeVarargs
	public final <T extends TempoAction> Stream<TempoActionWithState<T>> ofStateful(Class<T> actionClass, Class<? extends TempoState> requiredState, Class<? extends TempoState>... requiredStates) {
		Objects.requireNonNull(actionClass, "actionClass is required");
		Objects.requireNonNull(requiredState, "requiredState is required");
		Objects.requireNonNull(requiredStates, "requiredStates is required");

		ImmutableSet.Builder<Class<? extends TempoState>> allRequiredStates = ImmutableSet.builder();
		allRequiredStates.addAll(Arrays.asList(requiredStates));
		StatefulTempoFlowGenerator<T> generator = new StatefulTempoFlowGenerator<>(allRequiredStates.build(), downstreamBufferCapacity);
		statefulGeneratorsByClass.computeIfAbsent(actionClass, x -> new ArrayList<>()).add(generator);

		return Stream.generate(generator);
	}

	private static final class StatefulTempoFlowGenerator<T extends TempoAction> implements Supplier<TempoActionWithState<T>> {
		private final ImmutableSet<Class<? extends TempoState>> requiredState;
		private final BlockingQueue<TempoActionWithState<T>> queue;

		private StatefulTempoFlowGenerator(ImmutableSet<Class<? extends TempoState>> requiredState, int queueCapacity) {
			this.requiredState = requiredState;
			this.queue = new ArrayBlockingQueue<>(queueCapacity);
		}

		@Override
		public TempoActionWithState<T> get() {
			try {
				return queue.take();
			} catch (InterruptedException e) {
				throw new TempoException("Tempo flow generator was interrupted", e);
			}
		}

		public boolean accept(TempoActionWithState<T> o) {
			return queue.add(o);
		}

		public ImmutableSet<Class<? extends TempoState>> getRequiredState() {
			return requiredState;
		}
	}

	private static final class StatelessTempoFlowGenerator<T extends TempoAction> implements Supplier<T> {
		private final BlockingQueue<T> queue;

		private StatelessTempoFlowGenerator(int queueCapacity) {
			this.queue = new ArrayBlockingQueue<>(queueCapacity);
		}

		@Override
		public T get() {
			try {
				return queue.take();
			} catch (InterruptedException e) {
				throw new TempoException("Tempo flow generator was interrupted", e);
			}
		}

		public boolean accept(T o) {
			return queue.add(o);
		}
	}
}
