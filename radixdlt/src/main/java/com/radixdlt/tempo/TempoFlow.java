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

	public <T extends TempoAction> Stream<T> withStateless(Class<T> otherClass) {
		Objects.requireNonNull(otherClass, "otherClass is required");

		StatelessTempoFlowGenerator<T> generator = new StatelessTempoFlowGenerator<>(downstreamBufferCapacity);
		statelessGeneratorsByClass.computeIfAbsent(otherClass, x -> new ArrayList<>()).add(generator);

		return Stream.generate(generator);
	}

	public <T extends TempoAction> Stream<TempoActionWithState<T>> withStateful(Class<T> otherClass, Class<? extends TempoState> requiredState, Class<? extends TempoState>... requiredStates) {
		Objects.requireNonNull(otherClass, "otherClass is required");
		Objects.requireNonNull(requiredState, "otherClass is required");
		Objects.requireNonNull(requiredStates, "otherClass is required");

		ImmutableSet.Builder<Class<? extends TempoState>> allRequiredStates = ImmutableSet.builder();
		allRequiredStates.addAll(Arrays.asList(requiredStates));
		StatefulTempoFlowGenerator<T> generator = new StatefulTempoFlowGenerator<>(allRequiredStates.build(), downstreamBufferCapacity);
		statefulGeneratorsByClass.computeIfAbsent(otherClass, x -> new ArrayList<>()).add(generator);

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
