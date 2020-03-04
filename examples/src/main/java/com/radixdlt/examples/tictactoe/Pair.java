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

package com.radixdlt.examples.tictactoe;

import java.util.Objects;

/**
 * An immutable pair of elements.
 * <p>
 * Note that in reality, as for all containers,
 * instances of this class are only immutable
 * if their contained objects are also immutable.
 * @param <F> Type of the first element
 * @param <S> Type of the second element
 */
public final class Pair<F, S> {

    private final F first;
    private final S second;

    /**
     * Create a pair from the specified arguments.
     *
     * @param first  The first element of the pair.
     * @param second The second element of the pair.
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
     * @param first  The first element of the pair.
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

	@Override
	public int hashCode() {
		return Objects.hash(this.first, this.second);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof Pair<?, ?>) {
			Pair<?, ?> p = (Pair<?, ?>) obj;
			return Objects.equals(this.first, p.first) && Objects.equals(this.second, p.second);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[first=%s, second=%s]", getClass().getSimpleName(), first, second);
	}

}