/*
 * Copyright (c) 2020 Sergiy Yevtushenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.radixdlt.utils.functional;

import com.radixdlt.utils.functional.Functions.FN1;
import com.radixdlt.utils.functional.Functions.FN2;
import com.radixdlt.utils.functional.Functions.FN3;
import com.radixdlt.utils.functional.Functions.FN4;
import com.radixdlt.utils.functional.Functions.FN5;
import com.radixdlt.utils.functional.Functions.FN6;
import com.radixdlt.utils.functional.Functions.FN7;
import com.radixdlt.utils.functional.Functions.FN8;
import com.radixdlt.utils.functional.Functions.FN9;
import com.radixdlt.utils.functional.Tuple.Tuple1;
import com.radixdlt.utils.functional.Tuple.Tuple2;
import com.radixdlt.utils.functional.Tuple.Tuple3;
import com.radixdlt.utils.functional.Tuple.Tuple4;
import com.radixdlt.utils.functional.Tuple.Tuple5;
import com.radixdlt.utils.functional.Tuple.Tuple6;
import com.radixdlt.utils.functional.Tuple.Tuple7;
import com.radixdlt.utils.functional.Tuple.Tuple8;
import com.radixdlt.utils.functional.Tuple.Tuple9;

/**
 * Utility interfaces with transformable entities.
 */
public interface ResultMappers {

	/**
	 * Transformable entity for one {@Result}.
	 */
	interface Mapper1<T1> {
		Result<Tuple1<T1>> id();

		default <R> Result<R> map(final FN1<R, T1> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(final FN1<Result<R>, T1> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	/**
	 * Transformable entity for two {@Result}'s.
	 */
	interface Mapper2<T1, T2> {
		Result<Tuple2<T1, T2>> id();

		default <R> Result<R> map(final FN2<R, T1, T2> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(final FN2<Result<R>, T1, T2> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	/**
	 * Transformable entity for three {@Result}'s.
	 */
	interface Mapper3<T1, T2, T3> {
		Result<Tuple3<T1, T2, T3>> id();

		default <R> Result<R> map(final FN3<R, T1, T2, T3> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(final FN3<Result<R>, T1, T2, T3> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	/**
	 * Transformable entity for four {@Result}'s.
	 */
	interface Mapper4<T1, T2, T3, T4> {
		Result<Tuple4<T1, T2, T3, T4>> id();

		default <R> Result<R> map(final FN4<R, T1, T2, T3, T4> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(final FN4<Result<R>, T1, T2, T3, T4> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	/**
	 * Transformable entity for five {@Result}'s.
	 */
	interface Mapper5<T1, T2, T3, T4, T5> {
		Result<Tuple5<T1, T2, T3, T4, T5>> id();

		default <R> Result<R> map(final FN5<R, T1, T2, T3, T4, T5> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(final FN5<Result<R>, T1, T2, T3, T4, T5> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	/**
	 * Transformable entity for six {@Result}'s.
	 */
	interface Mapper6<T1, T2, T3, T4, T5, T6> {
		Result<Tuple6<T1, T2, T3, T4, T5, T6>> id();

		default <R> Result<R> map(final FN6<R, T1, T2, T3, T4, T5, T6> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(final FN6<Result<R>, T1, T2, T3, T4, T5, T6> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	/**
	 * Transformable entity for seven {@Result}'s.
	 */
	interface Mapper7<T1, T2, T3, T4, T5, T6, T7> {
		Result<Tuple7<T1, T2, T3, T4, T5, T6, T7>> id();

		default <R> Result<R> map(final FN7<R, T1, T2, T3, T4, T5, T6, T7> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(final FN7<Result<R>, T1, T2, T3, T4, T5, T6, T7> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	/**
	 * Transformable entity for eight {@Result}'s.
	 */
	interface Mapper8<T1, T2, T3, T4, T5, T6, T7, T8> {
		Result<Tuple8<T1, T2, T3, T4, T5, T6, T7, T8>> id();

		default <R> Result<R> map(final FN8<R, T1, T2, T3, T4, T5, T6, T7, T8> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(final FN8<Result<R>, T1, T2, T3, T4, T5, T6, T7, T8> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	/**
	 * Transformable entity for nine {@Result}'s.
	 */
	interface Mapper9<T1, T2, T3, T4, T5, T6, T7, T8, T9> {
		Result<Tuple9<T1, T2, T3, T4, T5, T6, T7, T8, T9>> id();

		default <R> Result<R> map(final FN9<R, T1, T2, T3, T4, T5, T6, T7, T8, T9> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(final FN9<Result<R>, T1, T2, T3, T4, T5, T6, T7, T8, T9> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}
}
