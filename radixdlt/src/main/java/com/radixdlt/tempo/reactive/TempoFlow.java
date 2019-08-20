package com.radixdlt.tempo.reactive;

import com.radixdlt.tempo.TempoStateBundle;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

// TODO document me!
public interface TempoFlow<T> {
	<R> TempoFlow<R> map(Function<? super T, ? extends R> mapper);

	<R> TempoFlow<R> flatMap(Function<? super T, Stream<? extends R>> mapper);

	TempoFlow<T> filter(Predicate<? super T> filter);

	void forEach(Consumer<T> consumer);

	<R> TempoFlow<R> mapStateful(BiFunction<? super T, TempoStateBundle, ? extends R> mapper,
	                             Class<? extends TempoState> requiredState,
	                             Class<? extends TempoState>... requiredStates);

	<R> TempoFlow<R> flatMapStateful(BiFunction<? super T, TempoStateBundle, Stream<? extends R>> mapper,
	                                 Class<? extends TempoState> requiredState,
	                                 Class<? extends TempoState>... requiredStates);

	TempoFlow<T> filterStateful(BiPredicate<? super T, TempoStateBundle> filter,
	                            Class<? extends TempoState> requiredState,
	                            Class<? extends TempoState>... requiredStates);

	void forEachStateful(BiConsumer<T, TempoStateBundle> consumer,
	                     Class<? extends TempoState> requiredState,
	                     Class<? extends TempoState>... requiredStates);

	static <T> TempoFlow<T> empty() {
		return TempoFlowSource.TempoFlowOp.empty();
	}

	@SafeVarargs
	static <T> TempoFlow<T> merge(TempoFlow<? extends T>... flows) {
		return TempoFlowSource.TempoFlowOp.merge(flows);
	}
}
