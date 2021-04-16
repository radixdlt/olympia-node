/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.utils;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * An immutable pair of elements.
 * <p>
 * Note that in reality, as for all containers,
 * instances of this class are only immutable
 * if their contained objects are also immutable.
 *
 * @param <F> Type of the first element
 * @param <S> Type of the second element
 */
public final class Pair<F, S> {
	private final F first;
	private final S second;

	/**
	 * Create a pair from the specified arguments.
	 *
	 * @param first The first element of the pair.
	 * @param second The second element of the pair.
	 *
	 * @return A {@link Pair} containing {@code first} and {@code second}.
	 */
	public static <A, B> Pair<A, B> of(final A first, final B second) {
		return new Pair<>(first, second);
	}

	/**
	 * Constructor for a pair of items.
	 * <p>
	 * Please consider using the factory method {@link #of(Object, Object)}
	 * instead of this constructor.
	 *
	 * @param first The first element of the pair.
	 * @param second The second element of the pair.
	 */
	public Pair(final F first, final S second) {
		this.first = first;
		this.second = second;
	}

	/**
	 * Retrieve the first element from this pair.
	 *
	 * @return The first element.
	 */
	public F getFirst() {
		return this.first;
	}

	/**
	 * Retrieve the second element from this pair.
	 *
	 * @return The second element.
	 */
	public S getSecond() {
		return this.second;
	}

	/**
	 * Maps the first element, returning a new pair with the mapped first
	 * element, and the original second element.
	 *
	 * @param mapper the mapper to apply to the first element
	 *
	 * @return the new pair
	 */
	public <R> Pair<R, S> mapFirst(Function<? super F, ? extends R> mapper) {
		return Pair.of(mapper.apply(this.first), this.second);
	}

	/**
	 * Maps the second element, returning a new pair with the original first
	 * element, and the mapped second element.
	 *
	 * @param mapper the mapper to apply to the second element
	 *
	 * @return the new pair
	 */
	public <R> Pair<F, R> mapSecond(Function<? super S, ? extends R> mapper) {
		return Pair.of(this.first, mapper.apply(this.second));
	}

	/**
	 * Maps both elements into single result.
	 *
	 * @param mapper the mapper to apply to both elements
	 *
	 * @return result of the mapping
	 */
	public <R> R map(BiFunction<? super F, ? super S, ? extends R> mapper) {
		return mapper.apply(first, second);
	}

	/**
	 * Returns {@code true} if the first element is non-null, {@code false}
	 * otherwise.
	 *
	 * @return {@code true} if the first element is non-null, else {@code false}
	 */
	public boolean firstNonNull() {
		return this.first != null;
	}

	/**
	 * Returns {@code true} if the first element is null, {@code false}
	 * otherwise.
	 *
	 * @return {@code true} if the first element is null, else {@code false}
	 */
	public boolean firstIsNull() {
		return this.first == null;
	}

	/**
	 * Returns {@code true} if the second element is non-null, {@code false}
	 * otherwise.
	 *
	 * @return {@code true} if the second element is non-null, else {@code false}
	 */
	public boolean secondNonNull() {
		return this.second != null;
	}

	/**
	 * Returns {@code true} if the second element is null, {@code false}
	 * otherwise.
	 *
	 * @return {@code true} if the second element is null, else {@code false}
	 */
	public boolean secondIsNull() {
		return this.second == null;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.first, this.second);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}

		if (!(obj instanceof Pair<?, ?>)) {
			return false;
		}

		var p = (Pair<?, ?>) obj;
		return Objects.equals(this.first, p.first) && Objects.equals(this.second, p.second);
	}

	@Override
	public String toString() {
		return String.format("%s[first=%s, second=%s]", getClass().getSimpleName(), first, second);
	}
}
