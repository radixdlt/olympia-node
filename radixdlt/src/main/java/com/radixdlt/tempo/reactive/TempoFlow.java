package com.radixdlt.tempo.reactive;

import com.google.errorprone.annotations.CheckReturnValue;
import com.radixdlt.tempo.TempoStateBundle;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

// TODO document me!
// TODO should be replaced with proper reactive framework down the line
public interface TempoFlow<T> {
	@CheckReturnValue
	<R> TempoFlow<R> map(Function<? super T, ? extends R> mapper);

	@CheckReturnValue
	<R> TempoFlow<R> flatMap(Function<? super T, Stream<? extends R>> mapper);

	@CheckReturnValue
	TempoFlow<T> filter(Predicate<? super T> filter);

	@CheckReturnValue
	TempoFlow<T> doOnNext(Consumer<? super T> consumer);

	void forEach(Consumer<T> consumer);

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@CheckReturnValue
	<R> TempoFlow<R> mapStateful(BiFunction<? super T, TempoStateBundle, ? extends R> mapper,
	                             Class<? extends TempoState> requiredState,
	                             Class<? extends TempoState>... requiredStates);

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@CheckReturnValue
	<R> TempoFlow<R> flatMapStateful(BiFunction<? super T, TempoStateBundle, Stream<? extends R>> mapper,
	                                 Class<? extends TempoState> requiredState,
	                                 Class<? extends TempoState>... requiredStates);

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@CheckReturnValue
	TempoFlow<T> filterStateful(BiPredicate<? super T, TempoStateBundle> filter,
	                            Class<? extends TempoState> requiredState,
	                            Class<? extends TempoState>... requiredStates);

	@SuppressWarnings({ "unchecked", "rawtypes" })
	void forEachStateful(BiConsumer<T, TempoStateBundle> consumer,
	                     Class<? extends TempoState> requiredState,
	                     Class<? extends TempoState>... requiredStates);

	@CheckReturnValue
	static <T> TempoFlow<T> empty() {
		return TempoFlowSource.TempoFlowOp.empty();
	}

	@SafeVarargs
	@CheckReturnValue
	static <T> TempoFlow<T> merge(TempoFlow<? extends T>... flows) {
		return TempoFlowSource.TempoFlowOp.merge(flows);
	}
}
