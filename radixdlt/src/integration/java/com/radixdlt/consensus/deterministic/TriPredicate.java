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

package com.radixdlt.consensus.deterministic;

import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Represents a predicate (boolean-valued function) of three arguments.  This is
 * the three-arity specialisation of {@link Predicate}.
 *
 * @param <T> the type of the first argument to the predicate
 * @param <U> the type of the second argument the predicate
 * @param <V> the type of the second argument the predicate
 *
 * @see Predicate
 * @see BiPredicate
 */
@FunctionalInterface
public interface TriPredicate<T, U, V> {

    /**
     * Evaluates this predicate on the specified arguments.
     *
     * @param t the first input argument
     * @param u the second input argument
     * @param v the third input argument
     * @return {@code true} if the input arguments match the predicate, otherwise {@code false}
     */
    boolean test(T t, U u, V v);

    default TriPredicate<T, U, V> and(TriPredicate<? super T, ? super U, ? super V> other) {
        Objects.requireNonNull(other);
        return (T t, U u, V v) -> test(t, u, v) && other.test(t, u, v);
    }

    default TriPredicate<T, U, V> negate() {
        return (T t, U u, V v) -> !test(t, u, v);
    }

    default TriPredicate<T, U, V> or(TriPredicate<? super T, ? super U, ? super V> other) {
        Objects.requireNonNull(other);
        return (T t, U u, V v) -> test(t, u, v) || other.test(t, u, v);
    }

    default BiPredicate<U, V> bindFirst(T t) {
    	return (U u, V v) -> test(t, u, v);
    }

    default BiPredicate<T, V> bindSecond(U u) {
    	return (T t, V v) -> test(t, u, v);
    }

    default BiPredicate<T, U> bindThird(V v) {
    	return (T t, U u) -> test(t, u, v);
    }
}
