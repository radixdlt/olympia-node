package com.radixdlt.tempo.reactive;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.tempo.TempoException;
import com.radixdlt.tempo.TempoStateBundle;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A Tempo Flow that can be passed to a reactive stream pipeline and accept actions to pass through generated pipelines
 */
public final class TempoFlowSource {
	private final Map<Class<? extends TempoAction>, List<Head<?>>> headsByClass;

	public TempoFlowSource() {
		this.headsByClass = new HashMap<>();
	}

	public static final class TempoFlowInjector {
		private static final Logger logger = Logging.getLogger("Tempo");

		private final ImmutableMap<Class<? extends TempoAction>, List<Head<?>>> headsByClass;

		private TempoFlowInjector(ImmutableMap<Class<? extends TempoAction>, List<Head<?>>> headsByClass) {
			this.headsByClass = headsByClass;
		}

		public void inject(TempoAction action, Function<Set<Class<? extends TempoState>>, TempoStateBundle> stateGenerator) {
			Objects.requireNonNull(action, "action is required");
			Class<? extends TempoAction> actionClass = action.getClass();

			// run flows starting at head
			List<Head<?>> heads = this.headsByClass.get(actionClass);
			if (heads != null) {
				if (logger.hasLevel(Logging.TRACE)) {
					logger.trace("Injecting action '" + actionClass.getSimpleName() + "' into " + heads.size() + " heads");
				}
				for (Head head : heads) {
					TempoStateBundle bundle = stateGenerator.apply(head.getRequiredState());
					head.accept(action, bundle);
				}
			} else if (logger.hasLevel(Logging.TRACE)) {
				logger.trace("No heads available to inject action '" + actionClass.getSimpleName() + "' into");
			}
		}
	}

	// TODO could check all links for integrity here
	public TempoFlowInjector toInjector() {
		return new TempoFlowInjector(ImmutableMap.copyOf(headsByClass));
	}

	/**
	 * Creates a flow of a certain tempo action type
	 * @param actionClass The action class of interest
	 * @param <T> The type of the action class
	 * @return A stream on which the flow is to be built
	 */
	public final <T extends TempoAction> TempoFlow<T> of(Class<T> actionClass) {
		Objects.requireNonNull(actionClass, "actionClass is required");

		Head<T> head = new Head<>();
		headsByClass.computeIfAbsent(actionClass, x -> new ArrayList<>()).add(head);
		return head;
	}

	// TODO move elsewhere
	private abstract static class Sink<T> {
		final Head<?> head;

		Sink(TempoFlowOp<?, T> upstream) {
			Objects.requireNonNull(upstream, "upstream is required");

			// link upstream
			if (upstream.linked) {
				throw new TempoException("Upstream already linked to '" + upstream.downstream + "'");
			}
			upstream.downstream = this;
			upstream.linked = true;

			this.head = upstream.head;
		}

		Sink(Head<T> head) {
			this.head = head;
		}

		abstract void accept(T value, TempoStateBundle bundle);
	}

	private static final class Head<T> extends TempoFlowOp<T, T> {
		private final Set<Class<? extends TempoState>> requiredState;

		private Head() {
			this.requiredState = new HashSet<>();
		}

		@Override
		void doAccept(T value, TempoStateBundle bundle) {
			downstream.accept(value, bundle);
		}

		Set<Class<? extends TempoState>> getRequiredState() {
			return requiredState;
		}

		@Override
		@SafeVarargs
		final void requireState(Class<? extends TempoState> requiredState, Class<? extends TempoState>... requiredStates) {
			Objects.requireNonNull(requiredState, "requiredState is required");
			Objects.requireNonNull(requiredStates, "requiredStates is required");
			this.requiredState.add(requiredState);
			this.requiredState.addAll(Arrays.asList(requiredStates));
		}
	}

	private abstract static class TempoFlowOp<T_IN, T_OUT> extends Sink<T_IN> implements TempoFlow<T_OUT> {
		Sink<T_OUT> downstream;
		private boolean linked;

		TempoFlowOp() {
			super(null);
		}

		TempoFlowOp(TempoFlowOp<?, T_IN> upstream) {
			super(upstream);
		}

		final void accept(T_IN value, TempoStateBundle bundle) {
			if (downstream == null) {
				throw new TempoException("Unlinked flow op");
			}
			doAccept(value, bundle);
		}

		abstract void doAccept(T_IN value, TempoStateBundle bundle);

		@Override
		public <R> TempoFlow<R> map(Function<? super T_OUT, ? extends R> mapper) {
			Objects.requireNonNull(mapper, "mapper is required");

			return new TempoFlowOp<T_OUT, R>(this) {
				@Override
				public void doAccept(T_OUT value, TempoStateBundle bundle) {
					downstream.accept(mapper.apply(value), bundle);
				}
			};
		}

		@Override
		public <R> TempoFlow<R> flatMap(Function<? super T_OUT, Stream<? extends R>> mapper) {
			Objects.requireNonNull(mapper, "mapper is required");

			return new TempoFlowOp<T_OUT, R>(this) {
				@Override
				public void doAccept(T_OUT value, TempoStateBundle bundle) {
					Stream<? extends R> valueStream = mapper.apply(value);
					valueStream.forEach(mappedValue -> downstream.accept(mappedValue, bundle));
				}
			};
		}

		@Override
		public TempoFlow<T_OUT> filter(Predicate<? super T_OUT> predicate) {
			Objects.requireNonNull(predicate, "predicate is required");

			return new TempoFlowOp<T_OUT, T_OUT>(this) {
				@Override
				protected void doAccept(T_OUT value, TempoStateBundle bundle) {
					if (predicate.test(value)) {
						downstream.accept(value, bundle);
					}
				}
			};
		}

		@Override
		public void forEach(Consumer<T_OUT> consumer) {
			Objects.requireNonNull(consumer, "consumer is required");

			new Sink<T_OUT>(this){
				@Override
				void accept(T_OUT value, TempoStateBundle bundle) {
					consumer.accept(value);
				}
			};
		}

		@Override
		@SafeVarargs
		public final <R> TempoFlow<R> map(BiFunction<? super T_OUT, TempoStateBundle, ? extends R> mapper,
		                                  Class<? extends TempoState> requiredState,
		                                  Class<? extends TempoState>... requiredStates) {
			Objects.requireNonNull(mapper, "mapper is required");
			requireState(requiredState, requiredStates);

			return new TempoFlowOp<T_OUT, R>(this) {
				@Override
				public void doAccept(T_OUT value, TempoStateBundle bundle) {
					downstream.accept(mapper.apply(value, bundle), bundle);
				}
			};
		}

		@Override
		@SafeVarargs
		public final <R> TempoFlow<R> flatMap(BiFunction<? super T_OUT, TempoStateBundle, Stream<? extends R>> mapper,
		                                      Class<? extends TempoState> requiredState,
		                                      Class<? extends TempoState>... requiredStates) {
			Objects.requireNonNull(mapper, "mapper is required");
			requireState(requiredState, requiredStates);

			return new TempoFlowOp<T_OUT, R>(this) {
				@Override
				public void doAccept(T_OUT value, TempoStateBundle bundle) {
					Stream<? extends R> valueStream = mapper.apply(value, bundle);
					valueStream.forEach(mappedValue -> downstream.accept(mappedValue, bundle));
				}
			};
		}

		@Override
		@SafeVarargs
		public final TempoFlow<T_OUT> filter(BiPredicate<? super T_OUT, TempoStateBundle> filter,
		                               Class<? extends TempoState> requiredState,
		                               Class<? extends TempoState>... requiredStates) {
			Objects.requireNonNull(filter, "filter is required");
			requireState(requiredState, requiredStates);

			return new TempoFlowOp<T_OUT, T_OUT>(this) {
				@Override
				protected void doAccept(T_OUT value, TempoStateBundle bundle) {
					if (filter.test(value, bundle)) {
						downstream.accept(value, bundle);
					}
				}
			};
		}

		@Override
		@SafeVarargs
		public final void forEach(BiConsumer<T_OUT, TempoStateBundle> consumer,
		                    Class<? extends TempoState> requiredState,
		                    Class<? extends TempoState>... requiredStates) {
			Objects.requireNonNull(consumer, "consumer is required");
			requireState(requiredState, requiredStates);

			new Sink<T_OUT>(this){
				@Override
				void accept(T_OUT value, TempoStateBundle bundle) {
					consumer.accept(value, bundle);
				}
			};
		}

		void requireState(Class<? extends TempoState> requiredState, Class<? extends TempoState>[] requiredStates) {
			if (head == null) {
				throw new TempoException("Tempo flow is detached from head");
			}
			head.requireState(requiredState, requiredStates);
		}
	}
}
