/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.constraintmachine;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.google.common.collect.Iterators;

/**
 * A disjunction of two values, with an interface that makes the "first"
 * value look like an {@link java.util.Optional}.
 * <p>
 * {@code null} is not permitted for either first or second value.
 *
 * @param <F> The "first" type
 * @param <S> The "second" type
 */
public abstract class Either<F, S> implements Iterable<F> {

	private Either() {
		// Can't construct outside this class
	}

	public static <F, S> Either<F, S> first(F first) {
		return new First<>(first);
	}

	public static <F, S> Either<F, S> second(S second) {
		return new Second<>(second);
	}

	// Convert to optional

	public final Optional<F> asOptional() {
		return isFirst() ? Optional.of(get()) : Optional.empty();
	}

	public final Optional<S> asOptionalSecond() {
		return isSecond() ? Optional.of(getSecond()) : Optional.empty();
	}

	// java.util.Optional equivalent methods for first and second

	public abstract F get();

	public abstract S getSecond();

	public final boolean isPresent() {
		return isFirst();
	}

	public abstract boolean isFirst();

	public abstract boolean isSecond();

	public final void ifFirst(Consumer<? super F> consumer) {
		if (isFirst()) {
			consumer.accept(get());
		}
	}

	public final void ifSecond(Consumer<? super S> consumer) {
		if (isSecond()) {
			consumer.accept(getSecond());
		}
	}

	public final Optional<Either<F, S>> filter(Predicate<? super F> predicate) {
		Objects.requireNonNull(predicate);
		return isSecond() || predicate.test(get()) ? Optional.of(this) : Optional.empty();
	}

	public final Either<F, S> filterOrElse(Predicate<? super F> predicate, Function<? super F, ? extends S> toSecond) {
		Objects.requireNonNull(predicate);
		Objects.requireNonNull(toSecond);
		if (isSecond() || predicate.test(get())) {
			return this;
		} else {
			return Either.second(toSecond.apply(get()));
		}
	}

	public final <U> Either<U, S> map(Function<? super F, ? extends U> mapper) {
		Objects.requireNonNull(mapper);
		if (isFirst()) {
			return Either.first(mapper.apply(get()));
		} else {
			// Type safety is OK here, as first value is not present.
			@SuppressWarnings("unchecked")
			Either<U, S> coerced = (Either<U, S>) this;
			return coerced;
		}
	}

	public final <U> Either<F, U> mapSecond(Function<? super S, ? extends U> secondMapper) {
		Objects.requireNonNull(secondMapper);
		if (isSecond()) {
			return Either.second(secondMapper.apply(getSecond()));
		} else {
			// Type safety is OK here, as second value is not present.
			@SuppressWarnings("unchecked")
			Either<F, U> coerced = (Either<F, U>) this;
			return coerced;
		}
	}

	public final <U> Either<U, S> flatMap(Function<? super F, ? extends Either<? extends U, S>> mapper) {
		Objects.requireNonNull(mapper);
		if (isFirst()) {
			// Type cast is valid here as ? extends X is assignable to X.
			@SuppressWarnings("unchecked")
			Either<U, S> coerced = (Either<U, S>) mapper.apply(get());
			return coerced;
		} else {
			// Type cast is valid here as first value is not present.
			@SuppressWarnings("unchecked")
			Either<U, S> coerced = (Either<U, S>) this;
			return coerced;
		}
	}

	public final <U> Either<F, U> flatMapSecond(Function<? super S, ? extends Either<F, ? extends U>> mapper) {
		Objects.requireNonNull(mapper);
		if (isSecond()) {
			// Type cast is valid here as ? extends X is assignable to X.
			@SuppressWarnings("unchecked")
			Either<F, U> coerced = (Either<F, U>) mapper.apply(getSecond());
			return coerced;
		} else {
			// Type cast is valid here as second value is not present.
			@SuppressWarnings("unchecked")
			Either<F, U> coerced = (Either<F, U>) this;
			return coerced;
		}
	}

	public final <U, V> Either<U, V> bimap(Function<? super F, ? extends U> firstMapper, Function<? super S, ? extends V> secondMapper) {
		Objects.requireNonNull(firstMapper);
		Objects.requireNonNull(secondMapper);
		if (isFirst()) {
			return first(firstMapper.apply(get()));
		} else {
			return second(secondMapper.apply(getSecond()));
		}
	}

	public final Either<F, S> orElse(Either<? extends F, ? extends S> other) {
		Objects.requireNonNull(other);
		if (isFirst()) {
			return this;
		}
		// Type safety OK here as ? extends X is assignable to X.
		@SuppressWarnings("unchecked")
		Either<F, S> coerced = (Either<F, S>) other;
		return coerced;
	}

	public final Either<F, S> orElseGet(Supplier<? extends Either<? extends F, ? extends S>> supplier) {
		Objects.requireNonNull(supplier);
		if (isFirst()) {
			return this;
		}
		// Type safety OK here as ? extends X is assignable to X.
		@SuppressWarnings("unchecked")
		Either<F, S> coerced = (Either<F, S>) supplier.get();
		return coerced;
	}

	public final <X extends Throwable> F orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
		Objects.requireNonNull(exceptionSupplier);
		if (isFirst()) {
			return get();
		} else {
			throw exceptionSupplier.get();
		}
	}

	public final <X extends Throwable> F orElseThrow(Function<? super S, X> exceptionFunction) throws X {
		Objects.requireNonNull(exceptionFunction);
		if (isFirst()) {
			return get();
		} else {
			throw exceptionFunction.apply(getSecond());
		}
	}

	public final Either<S, F> swap() {
		if (isFirst()) {
			return second(get());
		} else {
			return first(getSecond());
		}
	}

	@Override
	public final Iterator<F> iterator() {
		if (isFirst()) {
			return Iterators.singletonIterator(get());
		} else {
			return Collections.emptyIterator();
		}
	}

	public final Either<F, S> peek(Consumer<? super F> firstAction, Consumer<? super S> secondAction) {
		Objects.requireNonNull(firstAction);
		Objects.requireNonNull(secondAction);

		if (isFirst()) {
			firstAction.accept(get());
		} else {
			secondAction.accept(getSecond());
		}

		return this;
	}

	public final Either<F, S> peek(Consumer<? super F> action) {
		Objects.requireNonNull(action);
		if (isFirst()) {
			action.accept(get());
		}
		return this;
	}

	public final Either<F, S> peekSecond(Consumer<? super S> action) {
		Objects.requireNonNull(action);
		if (isSecond()) {
			action.accept(getSecond());
		}
		return this;
	}

	static final class First<F, S> extends Either<F, S> {
		private final F value;

		private First(F value) {
			this.value = Objects.requireNonNull(value);
		}

		@Override
		public F get() {
			return this.value;
		}

		@Override
		public S getSecond() {
			throw new NoSuchElementException();
		}

		@Override
		public boolean isFirst() {
			return true;
		}

		@Override
		public boolean isSecond() {
			return false;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}
			if (!(obj instanceof First)) {
				return false;
			}
			First<?, ?> that = (First<?, ?>) obj;
			return Objects.equals(this.value, that.value);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(this.value);
		}

		@Override
		public String toString() {
			return String.format("%s[%s]", getClass().getSimpleName(), this.value);
		}
	}

	public static final class Second<F, S> extends Either<F, S> {
		private final S value;

		private Second(S value) {
			this.value = Objects.requireNonNull(value);
		}

		@Override
		public F get() {
			throw new NoSuchElementException();
		}

		@Override
		public S getSecond() {
			return this.value;
		}

		@Override
		public boolean isFirst() {
			return false;
		}

		@Override
		public boolean isSecond() {
			return true;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}
			if (!(obj instanceof Second)) {
				return false;
			}
			Second<?, ?> that = (Second<?, ?>) obj;
			return Objects.equals(this.value, that.value);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(value);
		}

		@Override
		public String toString() {
			return String.format("%s[%s]", getClass().getSimpleName(), this.value);
		}
	}
}