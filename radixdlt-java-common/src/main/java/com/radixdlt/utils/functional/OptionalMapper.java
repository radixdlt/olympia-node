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

import java.util.Optional;

public interface OptionalMapper {
	interface Mapper1<T1> {
		Optional<Tuple1<T1>> id();

		default <R> Optional<R> map(final FN1<R, T1> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Optional<R> flatMap(final FN1<Optional<R>, T1> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	interface Mapper2<T1, T2> {
		Optional<Tuple2<T1, T2>> id();

		default <R> Optional<R> map(final FN2<R, T1, T2> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Optional<R> flatMap(final FN2<Optional<R>, T1, T2> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	interface Mapper3<T1, T2, T3> {
		Optional<Tuple3<T1, T2, T3>> id();

		default <R> Optional<R> map(final FN3<R, T1, T2, T3> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Optional<R> flatMap(final FN3<Optional<R>, T1, T2, T3> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	interface Mapper4<T1, T2, T3, T4> {
		Optional<Tuple4<T1, T2, T3, T4>> id();

		default <R> Optional<R> map(final FN4<R, T1, T2, T3, T4> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Optional<R> flatMap(final FN4<Optional<R>, T1, T2, T3, T4> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	interface Mapper5<T1, T2, T3, T4, T5> {
		Optional<Tuple5<T1, T2, T3, T4, T5>> id();

		default <R> Optional<R> map(final FN5<R, T1, T2, T3, T4, T5> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Optional<R> flatMap(final FN5<Optional<R>, T1, T2, T3, T4, T5> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	interface Mapper6<T1, T2, T3, T4, T5, T6> {
		Optional<Tuple6<T1, T2, T3, T4, T5, T6>> id();

		default <R> Optional<R> map(final FN6<R, T1, T2, T3, T4, T5, T6> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Optional<R> flatMap(final FN6<Optional<R>, T1, T2, T3, T4, T5, T6> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	interface Mapper7<T1, T2, T3, T4, T5, T6, T7> {
		Optional<Tuple7<T1, T2, T3, T4, T5, T6, T7>> id();

		default <R> Optional<R> map(final FN7<R, T1, T2, T3, T4, T5, T6, T7> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Optional<R> flatMap(final FN7<Optional<R>, T1, T2, T3, T4, T5, T6, T7> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	interface Mapper8<T1, T2, T3, T4, T5, T6, T7, T8> {
		Optional<Tuple8<T1, T2, T3, T4, T5, T6, T7, T8>> id();

		default <R> Optional<R> map(final FN8<R, T1, T2, T3, T4, T5, T6, T7, T8> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Optional<R> flatMap(final FN8<Optional<R>, T1, T2, T3, T4, T5, T6, T7, T8> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	interface Mapper9<T1, T2, T3, T4, T5, T6, T7, T8, T9> {
		Optional<Tuple9<T1, T2, T3, T4, T5, T6, T7, T8, T9>> id();

		default <R> Optional<R> map(final FN9<R, T1, T2, T3, T4, T5, T6, T7, T8, T9> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Optional<R> flatMap(final FN9<Optional<R>, T1, T2, T3, T4, T5, T6, T7, T8, T9> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}
}
