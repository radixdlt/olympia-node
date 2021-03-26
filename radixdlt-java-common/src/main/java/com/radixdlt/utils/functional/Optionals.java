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

import com.radixdlt.utils.functional.OptionalMapper.Mapper1;
import com.radixdlt.utils.functional.OptionalMapper.Mapper2;
import com.radixdlt.utils.functional.OptionalMapper.Mapper3;
import com.radixdlt.utils.functional.OptionalMapper.Mapper4;
import com.radixdlt.utils.functional.OptionalMapper.Mapper5;
import com.radixdlt.utils.functional.OptionalMapper.Mapper6;
import com.radixdlt.utils.functional.OptionalMapper.Mapper7;
import com.radixdlt.utils.functional.OptionalMapper.Mapper8;
import com.radixdlt.utils.functional.OptionalMapper.Mapper9;
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

import static com.radixdlt.utils.functional.Tuple.tuple;

/**
 * Useful utility methods for {@link Optional}.
 */
public final class Optionals {
	private Optionals() { }

	public static <T1> Optional<Tuple1<T1>> all(Optional<T1> op1) {
		return op1.flatMap(v1 -> Optional.of(tuple(v1)));
	}

	public static <T1, T2> Optional<Tuple2<T1, T2>> all(Optional<T1> op1, Optional<T2> op2) {
		return op1.flatMap(v1 -> op2.flatMap(v2 -> Optional.of(tuple(v1, v2))));
	}

	public static <T1, T2, T3> Optional<Tuple3<T1, T2, T3>> all(
		Optional<T1> op1, Optional<T2> op2, Optional<T3> op3
	) {
		return op1.flatMap(v1 -> op2.flatMap(v2 -> op3.flatMap(v3 -> Optional.of(tuple(v1, v2, v3)))));
	}

	public static <T1, T2, T3, T4> Optional<Tuple4<T1, T2, T3, T4>> all(
		Optional<T1> op1, Optional<T2> op2,
		Optional<T3> op3, Optional<T4> op4
	) {
		return op1.flatMap(
			v1 -> op2.flatMap(
				v2 -> op3.flatMap(
					v3 -> op4.flatMap(
						v4 -> Optional.of(tuple(v1, v2, v3, v4))))));
	}

	public static <T1, T2, T3, T4, T5> Optional<Tuple5<T1, T2, T3, T4, T5>> all(
		Optional<T1> op1, Optional<T2> op2, Optional<T3> op3,
		Optional<T4> op4, Optional<T5> op5
	) {
		return op1.flatMap(
			v1 -> op2.flatMap(
				v2 -> op3.flatMap(
					v3 -> op4.flatMap(
						v4 -> op5.flatMap(
							v5 -> Optional.of(tuple(v1, v2, v3, v4, v5)))))));
	}

	public static <T1, T2, T3, T4, T5, T6> Optional<Tuple6<T1, T2, T3, T4, T5, T6>> all(
		Optional<T1> op1, Optional<T2> op2, Optional<T3> op3,
		Optional<T4> op4, Optional<T5> op5, Optional<T6> op6
	) {
		return op1.flatMap(
			v1 -> op2.flatMap(
				v2 -> op3.flatMap(
					v3 -> op4.flatMap(
						v4 -> op5.flatMap(
							v5 -> op6.flatMap(
								v6 -> Optional.of(tuple(v1, v2, v3, v4, v5, v6))))))));
	}

	public static <T1, T2, T3, T4, T5, T6, T7> Optional<Tuple7<T1, T2, T3, T4, T5, T6, T7>> all(
		Optional<T1> op1, Optional<T2> op2, Optional<T3> op3,
		Optional<T4> op4, Optional<T5> op5, Optional<T6> op6,
		Optional<T7> op7
	) {
		return op1.flatMap(
			v1 -> op2.flatMap(
				v2 -> op3.flatMap(
					v3 -> op4.flatMap(
						v4 -> op5.flatMap(
							v5 -> op6.flatMap(
								v6 -> op7.flatMap(
									v7 -> Optional.of(tuple(v1, v2, v3, v4, v5, v6, v7)))))))));
	}

	public static <T1, T2, T3, T4, T5, T6, T7, T8> Optional<Tuple8<T1, T2, T3, T4, T5, T6, T7, T8>> all(
		Optional<T1> op1, Optional<T2> op2, Optional<T3> op3,
		Optional<T4> op4, Optional<T5> op5, Optional<T6> op6,
		Optional<T7> op7, Optional<T8> op8
	) {
		return op1.flatMap(
			v1 -> op2.flatMap(
				v2 -> op3.flatMap(
					v3 -> op4.flatMap(
						v4 -> op5.flatMap(
							v5 -> op6.flatMap(
								v6 -> op7.flatMap(
									v7 -> op8.flatMap(
										v8 -> Optional.of(tuple(v1, v2, v3, v4, v5, v6, v7, v8))))))))));
	}

	public static <T1, T2, T3, T4, T5, T6, T7, T8, T9> Optional<Tuple9<T1, T2, T3, T4, T5, T6, T7, T8, T9>> all(
		Optional<T1> op1, Optional<T2> op2, Optional<T3> op3,
		Optional<T4> op4, Optional<T5> op5, Optional<T6> op6,
		Optional<T7> op7, Optional<T8> op8, Optional<T9> op9
	) {
		return op1.flatMap(
			v1 -> op2.flatMap(
				v2 -> op3.flatMap(
					v3 -> op4.flatMap(
						v4 -> op5.flatMap(
							v5 -> op6.flatMap(
								v6 -> op7.flatMap(
									v7 -> op8.flatMap(
										v8 -> op9.flatMap(
											v9 -> Optional.of(
												tuple(v1, v2, v3, v4, v5, v6, v7, v8, v9)
											))))))))));
	}

	public static <T1> Mapper1<T1> allOf(Optional<T1> op1) {
		return () -> all(op1);
	}

	public static <T1, T2> Mapper2<T1, T2> allOf(Optional<T1> op1, Optional<T2> op2) {
		return () -> all(op1, op2);
	}

	public static <T1, T2, T3> Mapper3<T1, T2, T3> allOf(Optional<T1> op1, Optional<T2> op2, Optional<T3> op3) {
		return () -> all(op1, op2, op3);
	}

	public static <T1, T2, T3, T4> Mapper4<T1, T2, T3, T4> allOf(
		Optional<T1> op1, Optional<T2> op2,
		Optional<T3> op3, Optional<T4> op4
	) {
		return () -> all(op1, op2, op3, op4);
	}

	public static <T1, T2, T3, T4, T5> Mapper5<T1, T2, T3, T4, T5> allOf(
		Optional<T1> op1, Optional<T2> op2, Optional<T3> op3,
		Optional<T4> op4, Optional<T5> op5
	) {
		return () -> all(op1, op2, op3, op4, op5);
	}

	public static <T1, T2, T3, T4, T5, T6> Mapper6<T1, T2, T3, T4, T5, T6> allOf(
		Optional<T1> op1, Optional<T2> op2, Optional<T3> op3,
		Optional<T4> op4, Optional<T5> op5, Optional<T6> op6
	) {
		return () -> all(op1, op2, op3, op4, op5, op6);
	}

	public static <T1, T2, T3, T4, T5, T6, T7> Mapper7<T1, T2, T3, T4, T5, T6, T7> allOf(
		Optional<T1> op1, Optional<T2> op2, Optional<T3> op3,
		Optional<T4> op4, Optional<T5> op5, Optional<T6> op6,
		Optional<T7> op7
	) {
		return () -> all(op1, op2, op3, op4, op5, op6, op7);
	}

	public static <T1, T2, T3, T4, T5, T6, T7, T8> Mapper8<T1, T2, T3, T4, T5, T6, T7, T8> allOf(
		Optional<T1> op1, Optional<T2> op2, Optional<T3> op3,
		Optional<T4> op4, Optional<T5> op5, Optional<T6> op6,
		Optional<T7> op7, Optional<T8> op8
	) {
		return () -> all(op1, op2, op3, op4, op5, op6, op7, op8);
	}

	public static <T1, T2, T3, T4, T5, T6, T7, T8, T9> Mapper9<T1, T2, T3, T4, T5, T6, T7, T8, T9> allOf(
		Optional<T1> op1, Optional<T2> op2, Optional<T3> op3,
		Optional<T4> op4, Optional<T5> op5, Optional<T6> op6,
		Optional<T7> op7, Optional<T8> op8, Optional<T9> op9
	) {
		return () -> all(op1, op2, op3, op4, op5, op6, op7, op8, op9);
	}
}
