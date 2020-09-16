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
import java.util.function.Supplier;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterators;

/**
 * A disjunction of two values, with an interface that makes the "first"
 * value look like an {@link java.util.Optional}.
 * <p>
 * By convention, "first" is used to return values, and "second" is used
 * to return error codes/messages.
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

	/**
	 * Constructs an {@code Either} with the specified non-null "first" value.
	 *
	 * @param <F> The type of the "first" value
	 * @param <S> The type of the "second" value
	 * @param first The non-null value
	 * @return A freshly created {@code Either} containing the specified
	 * 		"first" value
	 */
	public static <F, S> Either<F, S> first(F first) {
		return new First<>(first);
	}

	/**
	 * Constructs an {@code Either} with the specified non-null "second" value.
	 *
	 * @param <F> The type of the "first" value
	 * @param <S> The type of the "second" value
	 * @param second The non-null value
	 * @return A freshly created {@code Either} containing the specified
	 * 		"second" value
	 */
	public static <F, S> Either<F, S> second(S second) {
		return new Second<>(second);
	}

	/**
	 * Returns the "first" value as an {@link Optional}.
	 * If this value is a "second", the {@code Optional} will be empty.
	 *
	 * @return The "first" value as an optional
	 */
	public final Optional<F> asOptional() {
		return isFirst() ? Optional.of(get()) : Optional.empty();
	}

	/**
	 * Returns the "second" value as an {@link Optional}.
	 * If this value is a "first", the {@code Optional} will be empty.
	 *
	 * @return The "second" value as an optional
	 */
	public final Optional<S> asOptionalSecond() {
		return isSecond() ? Optional.of(getSecond()) : Optional.empty();
	}

	// java.util.Optional equivalent methods for first and second

    /**
     * Returns {@code true} if this is a "first" value , otherwise {@code false}.
     *
     * @return {@code true} if this is a "first" value , otherwise {@code false}
     */
	public final boolean isPresent() {
		return isFirst();
	}

    /**
     * If this is a "first", invoke the specified consumer with the value,
     * otherwise do nothing.
     *
     * @param consumer block to be executed if a value is present
     * @throws NullPointerException if value is present and {@code consumer}
     * 		is null
     */
	public final void ifPresent(Consumer<? super F> consumer) {
		ifFirst(consumer);
	}

    /**
     * If this is a "first", returns the value, otherwise throws
     * {@code NoSuchElementException}.
     *
     * @return the non-null value held by this {@code Either}
     * @throws NoSuchElementException if there is no first value present
     */
	public abstract F get();

    /**
     * If this is a "second", returns the value, otherwise throws
     * {@code NoSuchElementException}.
     *
     * @return the non-null value held by this {@code Either}
     * @throws NoSuchElementException if there is no second value present
     */
	public abstract S getSecond();

    /**
     * Returns {@code true} if this is a "first" value , otherwise {@code false}.
     *
     * @return {@code true} if this is a "first" value , otherwise {@code false}
     */
	public abstract boolean isFirst();

    /**
     * Returns {@code true} if this is a "second" value , otherwise {@code false}.
     *
     * @return {@code true} if this is a "second" value , otherwise {@code false}
     */
	public abstract boolean isSecond();

    /**
     * If this is a "first", invoke the specified consumer with the value,
     * otherwise do nothing.
     *
     * @param consumer block to be executed if a first value is present
     * @throws NullPointerException if value is present and {@code consumer}
     * 		is null
     */
	public abstract void ifFirst(Consumer<? super F> consumer);

    /**
     * If this is a "second", invoke the specified consumer with the value,
     * otherwise do nothing.
     *
     * @param consumer block to be executed if a second value is present
     * @throws NullPointerException if value is present and {@code consumer}
     * 		is null
     */
	public abstract void ifSecond(Consumer<? super S> consumer);

    /**
     * If this is a "first", apply the provided mapping function to it,
     * returning a "first" containing the result.
     *
     * @param <U> The type of the result of the mapping function
     * @param mapper a mapping function to apply if this is a "first"
     * @return an {@code Either} with the result of applying a mapping
     * function to the value of this {@code Either} if this is a "first",
     * otherwise returns {@code this}.
     * @throws NullPointerException if the mapping function is null
     */
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

    /**
     * If this is a "second", apply the provided mapping function to it,
     * returning a "second" containing the result.
     *
     * @param <U> The type of the result of the mapping function
     * @param mapper a mapping function to apply if this is a "first"
     * @return an {@code Either} with the result of applying the mapping
     * function to the value of this {@code Either} if this is a "second",
     * otherwise returns {@code this}.
     * @throws NullPointerException if the mapping function is null
     */
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

    /**
     * If this is a "first", apply the provided {@code Either}-bearing
     * mapping function to it and return that result, otherwise return
     * {@code this}.  This method is similar to {@link #map(Function)},
     * but the provided mapper is one whose result is already an
     * {@code Either}, and {@code flatMap} does not wrap it further.
     *
     * @param <U> The type parameter of the first value of the {@code Either}
     * 		returned
     * @param mapper a mapping function to apply to the value, if this is a
     *		"first"
     * @return the result of applying an {@code Either}-bearing mapping
     *		function to the value of this {@code Either}, if this is a "first"
     * 		otherwise returns {@code this}
     * @throws NullPointerException if the mapping function is null or returns
     *		a null result
     */
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

    /**
     * If this is a "second", apply the provided {@code Either}-bearing
     * mapping function to it and return that result, otherwise return
     * {@code this}.  This method is similar to {@link #mapSecond(Function)},
     * but the provided mapper is one whose result is already an
     * {@code Either}, and {@code flatMap} does not wrap it further.
     *
     * @param <U> The type parameter of the second value of the {@code Either}
     * 		returned
     * @param mapper a mapping function to apply to the value, if this is a
     *		"second"
     * @return the result of applying an {@code Either}-bearing mapping
     *		function to the value of this {@code Either}, if this is a "second"
     * 		otherwise returns {@code this}
     * @throws NullPointerException if the mapping function is null or returns
     *		a null result
     */
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

    /**
     * Maps this {@link Either} using one of the specified mappers as
     * appropriate, depending on whether this is a "first" or "second".
     * <p>
     * Equivalent to:
     * <pre>
     *   either.map(firstMapper).mapSecond(secondMapper)
     * </pre>
     *
     * @param <U> The type parameter of the first value of the {@code Either}
     * 		returned
     * @param <V> The type parameter of the second value of the {@code Either}
     * 		returned
     * @param firstMapper a mapping function to apply to the first value,
     *		if this is a "first"
     * @param secondMapper a mapping function to apply to the second value,
     *		if this is a "second"
     * @return the result of applying either {@code firstMapper} or
     * 		{@code secondMapper}, as appropriate, to this
     * @throws NullPointerException if either mapping function is null or returns
     *		a null result
     */
	public final <U, V> Either<U, V> bimap(Function<? super F, ? extends U> firstMapper, Function<? super S, ? extends V> secondMapper) {
		Objects.requireNonNull(firstMapper);
		Objects.requireNonNull(secondMapper);
		if (isFirst()) {
			return first(firstMapper.apply(get()));
		} else {
			return second(secondMapper.apply(getSecond()));
		}
	}

    /**
     * If this is a "first", returns the value, otherwise returns
     * {@code other}.
     *
     * @param other the value to be returned if this is not a "first"
     * @return the first value, if present, otherwise {@code other}
     */
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

    /**
     * Return the first value if present, otherwise invoke {@code other} and
     * return the result of that invocation.
     *
     * @param other a {@code Supplier} whose result is returned if this is not
     *		a "first"
     * @return the first value if present, otherwise the result of
     *		{@code other.get()}
     * @throws NullPointerException if {@code supplier} is null
     */
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

    /**
     * Return the first value, if present, otherwise throw an exception
     * created by the provided supplier.
     *
     * @param <X> Type of the exception to be thrown
     * @param exceptionSupplier The supplier which will return the exception
     *		to be thrown
     * @return the first value
     * @throws X if this is not a "first"
     * @throws NullPointerException if {@code exceptionSupplier} is null
     */
	public abstract <X extends Throwable> F orElseThrow(Supplier<? extends X> exceptionSupplier) throws X;

    /**
     * Return the first value, if present, otherwise throw an exception
     * created by the provided function called with the second value.
     *
     * @param <X> Type of the exception to be thrown
     * @param exceptionFunction The function which will return the exception
     *		to be thrown when called with the second value
     * @return the first value
     * @throws X if this is not a "first"
     * @throws NullPointerException if {@code exceptionSupplier} is null
     */
	public abstract <X extends Throwable> F orElseThrow(Function<? super S, X> exceptionFunction) throws X;

	/**
	 * Swaps the first and second values, returning a "second" if this is a
	 * "first" or vice-versa.
	 *
	 * @return a swapped {@code Either}
	 */
	public abstract Either<S, F> swap();

	/**
	 * If this {@link Either} is a "first", return an iterator over its
	 * value, otherwise an empty iterator.
	 *
	 * @return an iterator over the first value
	 */
	@Override
	public abstract Iterator<F> iterator();

	@VisibleForTesting
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
		public void ifFirst(Consumer<? super F> consumer) {
			consumer.accept(this.value);
		}

		@Override
		public void ifSecond(Consumer<? super S> consumer) {
			// Nothing for first
		}

		@Override
		public <X extends Throwable> F orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
			Objects.requireNonNull(exceptionSupplier);
			return this.value;
		}

		@Override
		public <X extends Throwable> F orElseThrow(Function<? super S, X> exceptionFunction) throws X {
			Objects.requireNonNull(exceptionFunction);
			return this.value;
		}

		@Override
		public Either<S, F> swap() {
			return second(this.value);
		}

		@Override
		public Iterator<F> iterator() {
			return Iterators.singletonIterator(this.value);
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

	@VisibleForTesting
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
		public void ifFirst(Consumer<? super F> consumer) {
			// Nothing for second
		}

		@Override
		public void ifSecond(Consumer<? super S> consumer) {
			consumer.accept(this.value);
		}

		@Override
		public <X extends Throwable> F orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
			throw exceptionSupplier.get();
		}

		@Override
		public <X extends Throwable> F orElseThrow(Function<? super S, X> exceptionFunction) throws X {
			throw exceptionFunction.apply(getSecond());
		}

		@Override
		public Either<S, F> swap() {
			return first(this.value);
		}

		@Override
		public Iterator<F> iterator() {
			return Collections.emptyIterator();
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
			return Objects.hashCode(this.value);
		}

		@Override
		public String toString() {
			return String.format("%s[%s]", getClass().getSimpleName(), this.value);
		}
	}
}