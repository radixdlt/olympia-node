/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.utils.functional;

/**
 * Collection of basic functions which accept 0-9 parameters and return single result.
 * Note that these functions are not supposed to throw any exceptions.
 */
public interface Functions {
    @FunctionalInterface
    interface FN0<R> {
        R apply();
    }

    @FunctionalInterface
    interface FN1<R, T1> {
        R apply(T1 param1);

        default FN0<R> bind(final T1 param) {
            return () -> apply(param);
        }

        default <N> FN1<N, T1> then(final FN1<N, R> function) {
            return v1 -> function.apply(apply(v1));
        }

        default <N> FN1<R, N> before(final FN1<T1, N> function) {
            return v1 -> apply(function.apply(v1));
        }

        static <T> FN1<T, T> id() {
            return v -> v;
        }
    }

    @FunctionalInterface
    interface FN2<R, T1, T2> {
        R apply(T1 param1, T2 param2);

        default FN1<R, T2> bind(final T1 param) {
            return v2 -> apply(param, v2);
        }

        default <N> FN2<N, T1, T2> then(final FN1<N, R> function) {
            return (v1, v2) -> function.apply(apply(v1, v2));
        }
    }

    @FunctionalInterface
    interface FN3<R, T1, T2, T3> {
        R apply(T1 param1, T2 param2, T3 param3);

        default FN2<R, T2, T3> bind(final T1 param) {
            return (v2, v3) -> apply(param, v2, v3);
        }

        default <N> FN3<N, T1, T2, T3> then(final FN1<N, R> function) {
            return (v1, v2, v3) -> function.apply(apply(v1, v2, v3));
        }
    }

    @FunctionalInterface
    interface FN4<R, T1, T2, T3, T4> {
        R apply(T1 param1, T2 param2, T3 param3, T4 param4);

        default FN3<R, T2, T3, T4> bind(final T1 param) {
            return (v2, v3, v4) -> apply(param, v2, v3, v4);
        }

        default <N> FN4<N, T1, T2, T3, T4> then(final FN1<N, R> function) {
            return (v1, v2, v3, v4) -> function.apply(apply(v1, v2, v3, v4));
        }
    }

    @FunctionalInterface
    interface FN5<R, T1, T2, T3, T4, T5> {
        R apply(T1 param1, T2 param2, T3 param3, T4 param4, T5 param5);

        default FN4<R, T2, T3, T4, T5> bind(final T1 param) {
            return (v2, v3, v4, v5) -> apply(param, v2, v3, v4, v5);
        }

        default <N> FN5<N, T1, T2, T3, T4, T5> then(final FN1<N, R> function) {
            return (v1, v2, v3, v4, v5) -> function.apply(apply(v1, v2, v3, v4, v5));
        }
    }

    @FunctionalInterface
    interface FN6<R, T1, T2, T3, T4, T5, T6> {
        R apply(T1 param1, T2 param2, T3 param3, T4 param4, T5 param5, T6 param6);

        default FN5<R, T2, T3, T4, T5, T6> bind(final T1 param) {
            return (v2, v3, v4, v5, v6) -> apply(param, v2, v3, v4, v5, v6);
        }

        default <N> FN6<N, T1, T2, T3, T4, T5, T6> then(final FN1<N, R> function) {
            return (v1, v2, v3, v4, v5, v6) -> function.apply(apply(v1, v2, v3, v4, v5, v6));
        }
    }

    @FunctionalInterface
    interface FN7<R, T1, T2, T3, T4, T5, T6, T7> {
        R apply(T1 param1, T2 param2, T3 param3, T4 param4, T5 param5, T6 param6, T7 param7);

        default FN6<R, T2, T3, T4, T5, T6, T7> bind(final T1 param) {
            return (v2, v3, v4, v5, v6, v7) -> apply(param, v2, v3, v4, v5, v6, v7);
        }

        default <N> FN7<N, T1, T2, T3, T4, T5, T6, T7> then(final FN1<N, R> function) {
            return (v1, v2, v3, v4, v5, v6, v7) -> function.apply(apply(v1, v2, v3, v4, v5, v6, v7));
        }
    }

    @FunctionalInterface
    interface FN8<R, T1, T2, T3, T4, T5, T6, T7, T8> {
        R apply(T1 param1, T2 param2, T3 param3, T4 param4, T5 param5, T6 param6, T7 param7, T8 param8);

        default FN7<R, T2, T3, T4, T5, T6, T7, T8> bind(final T1 param) {
            return (v2, v3, v4, v5, v6, v7, v8) -> apply(param, v2, v3, v4, v5, v6, v7, v8);
        }

        default <N> FN8<N, T1, T2, T3, T4, T5, T6, T7, T8> then(final FN1<N, R> function) {
            return (v1, v2, v3, v4, v5, v6, v7, v8) -> function.apply(apply(v1, v2, v3, v4, v5, v6, v7, v8));
        }
    }

    @FunctionalInterface
    interface FN9<R, T1, T2, T3, T4, T5, T6, T7, T8, T9> {
        R apply(T1 param1, T2 param2, T3 param3, T4 param4, T5 param5, T6 param6, T7 param7, T8 param8, T9 param9);

        default FN8<R, T2, T3, T4, T5, T6, T7, T8, T9> bind(final T1 param) {
            return (v2, v3, v4, v5, v6, v7, v8, v9) -> apply(param, v2, v3, v4, v5, v6, v7, v8, v9);
        }

        default <N> FN9<N, T1, T2, T3, T4, T5, T6, T7, T8, T9> then(final FN1<N, R> function) {
            return (v1, v2, v3, v4, v5, v6, v7, v8, v9) -> function.apply(apply(v1, v2, v3, v4, v5, v6, v7, v8, v9));
        }
    }
}
