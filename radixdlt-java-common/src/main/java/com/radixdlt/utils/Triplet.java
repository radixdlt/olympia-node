/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.utils;

import java.util.Objects;

/**
 * An immutable tripled of elements.
 * <p>
 * Note that in reality, as for all containers,
 * instances of this class are only immutable
 * if their contained objects are also immutable.
 *
 * @param <F> Type of the first element
 * @param <S> Type of the second element
 * @param <T> Type of the third element
 */
public final class Triplet<F, S, T> {
	private final F first;
	private final S second;
	private final T third;

	/**
	 * Create a pair from the specified arguments.
	 *
	 * @param first The first element of the pair.
	 * @param second The second element of the pair.
	 *
	 * @return A {@link Triplet} containing {@code first} and {@code second}.
	 */
	public static <A, B, C> Triplet<A, B, C> of(final A first, final B second, final C third) {
		return new Triplet<>(first, second, third);
	}

	/**
	 * Constructor for a pair of items.
	 * <p>
	 * Please consider using the factory method {@link #of(Object, Object, Object)}
	 * instead of this constructor.
	 *
	 * @param first The first element of the pair.
	 * @param second The second element of the pair.
	 */
	public Triplet(final F first, final S second, T third) {
		this.first = first;
		this.second = second;
		this.third = third;
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
	 * Retrieve the third element from this pair.
	 *
	 * @return The third element.
	 */
	public T getThird() {
		return this.third;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}

		if (!(obj instanceof Triplet<?, ?, ?>)) {
			return false;
		}

		final var p = (Triplet<?, ?, ?>) obj;
		return Objects.equals(this.first, p.first)
			&& Objects.equals(this.second, p.second)
			&& Objects.equals(this.third, p.third);
	}

	@Override
	public int hashCode() {
		return Objects.hash(first, second, third);
	}

	@Override
	public String toString() {
		return String.format("%s[%s, %s, %s]", getClass().getSimpleName(), first, second, third);
	}
}
