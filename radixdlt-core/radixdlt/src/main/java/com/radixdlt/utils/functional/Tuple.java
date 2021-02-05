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

import com.radixdlt.utils.functional.Functions.FN0;
import com.radixdlt.utils.functional.Functions.FN1;
import com.radixdlt.utils.functional.Functions.FN2;
import com.radixdlt.utils.functional.Functions.FN3;
import com.radixdlt.utils.functional.Functions.FN4;
import com.radixdlt.utils.functional.Functions.FN5;
import com.radixdlt.utils.functional.Functions.FN6;
import com.radixdlt.utils.functional.Functions.FN7;
import com.radixdlt.utils.functional.Functions.FN8;
import com.radixdlt.utils.functional.Functions.FN9;

import java.util.Objects;
import java.util.StringJoiner;

/**
 * Tuples with various size and convenient static factories for tuple creation.<br/>
 */
public interface Tuple<S extends Tuple> {
	interface Tuple0 extends Tuple<Tuple0> {
		<T> T map(FN0<T> mapper);
	}

	interface Tuple1<T1> extends Tuple<Tuple1<T1>> {
		<T> T map(FN1<T, T1> mapper);
	}

	interface Tuple2<T1, T2> extends Tuple {
		<T> T map(FN2<T, T1, T2> mapper);
	}

	interface Tuple3<T1, T2, T3> extends Tuple {
		<T> T map(FN3<T, T1, T2, T3> mapper);
	}

	interface Tuple4<T1, T2, T3, T4> extends Tuple {
		<T> T map(FN4<T, T1, T2, T3, T4> mapper);
	}

	interface Tuple5<T1, T2, T3, T4, T5> extends Tuple {
		<T> T map(FN5<T, T1, T2, T3, T4, T5> mapper);
	}

	interface Tuple6<T1, T2, T3, T4, T5, T6> extends Tuple {
		<T> T map(FN6<T, T1, T2, T3, T4, T5, T6> mapper);
	}

	interface Tuple7<T1, T2, T3, T4, T5, T6, T7> extends Tuple {
		<T> T map(FN7<T, T1, T2, T3, T4, T5, T6, T7> mapper);
	}

	interface Tuple8<T1, T2, T3, T4, T5, T6, T7, T8> extends Tuple {
		<T> T map(FN8<T, T1, T2, T3, T4, T5, T6, T7, T8> mapper);
	}

	interface Tuple9<T1, T2, T3, T4, T5, T6, T7, T8, T9> extends Tuple {
		<T> T map(FN9<T, T1, T2, T3, T4, T5, T6, T7, T8, T9> mapper);
	}

	Tuple0 TUPLE_0 = new Tuple0() {
		@Override
		public <T> T map(final FN0<T> mapper) {
			return mapper.apply();
		}

		@Override
		public boolean equals(final Object obj) {
			return this == obj || obj instanceof Tuple0;
		}

		@Override
		public int hashCode() {
			return super.hashCode();
		}

		@Override
		public String toString() {
			return "Tuple0()";
		}
	};

	static Tuple0 tuple() {
		return TUPLE_0;
	}

	static <T1> Tuple1<T1> tuple(final T1 param1) {
		return new Tuple1<>() {
			@Override
			public <T> T map(final FN1<T, T1> mapper) {
				return mapper.apply(param1);
			}

			@Override
			public boolean equals(final Object obj) {
				if (this == obj) {
					return true;
				}

				if (obj instanceof Tuple1<?>) {
					var tuple1 = (Tuple1<?>) obj;
					return tuple1.map(v1 -> Objects.equals(v1, param1));
				}
				return false;
			}

			@Override
			public int hashCode() {
				return Objects.hash(param1);
			}

			@Override
			public String toString() {
				return new StringJoiner(", ", "Tuple(", ")")
					.add(param1.toString())
					.toString();
			}
		};
	}

	static <T1, T2> Tuple2<T1, T2> tuple(final T1 param1, final T2 param2) {
		return new Tuple2<>() {
			@Override
			public <T> T map(final FN2<T, T1, T2> mapper) {
				return mapper.apply(param1, param2);
			}

			@Override
			public boolean equals(final Object obj) {
				if (this == obj) {
					return true;
				}

				if (obj instanceof Tuple2<?, ?>) {
					var tuple2 = (Tuple2<?, ?>) obj;

					return tuple2.map((v1, v2) ->
										  Objects.equals(v1, param1)
											  && Objects.equals(v2, param2));
				}
				return false;
			}

			@Override
			public int hashCode() {
				return Objects.hash(param1, param2);
			}

			@Override
			public String toString() {
				return new StringJoiner(", ", "Tuple(", ")")
					.add(param1.toString())
					.add(param2.toString())
					.toString();
			}
		};
	}

	static <T1, T2, T3> Tuple3<T1, T2, T3> tuple(final T1 param1, final T2 param2, final T3 param3) {
		return new Tuple3<>() {
			@Override
			public <T> T map(final FN3<T, T1, T2, T3> mapper) {
				return mapper.apply(param1, param2, param3);
			}

			@Override
			public boolean equals(final Object obj) {
				if (this == obj) {
					return true;
				}

				if (obj instanceof Tuple3<?, ?, ?>) {
					var tuple3 = (Tuple3<?, ?, ?>) obj;

					return tuple3.map((v1, v2, v3) -> Objects.equals(v1, param1)
						&& Objects.equals(v2, param2)
						&& Objects.equals(v3, param3));
				}
				return false;
			}

			@Override
			public int hashCode() {
				return Objects.hash(param1, param2, param3);
			}

			@Override
			public String toString() {
				return new StringJoiner(", ", "Tuple(", ")")
					.add(param1.toString())
					.add(param2.toString())
					.add(param3.toString())
					.toString();
			}
		};
	}

	static <T1, T2, T3, T4> Tuple4<T1, T2, T3, T4> tuple(
		final T1 param1,
		final T2 param2,
		final T3 param3,
		final T4 param4
	) {
		return new Tuple4<>() {
			@Override
			public <T> T map(final FN4<T, T1, T2, T3, T4> mapper) {
				return mapper.apply(param1, param2, param3, param4);
			}

			@Override
			public boolean equals(final Object obj) {
				if (this == obj) {
					return true;
				}

				if (obj instanceof Tuple4<?, ?, ?, ?>) {
					var tuple4 = (Tuple4<?, ?, ?, ?>) obj;

					return tuple4.map((v1, v2, v3, v4) -> Objects.equals(v1, param1)
						&& Objects.equals(v2, param2)
						&& Objects.equals(v3, param3)
						&& Objects.equals(v4, param4));
				}
				return false;
			}

			@Override
			public int hashCode() {
				return Objects.hash(param1, param2, param3, param4);
			}

			@Override
			public String toString() {
				return new StringJoiner(", ", "Tuple(", ")")
					.add(param1.toString())
					.add(param2.toString())
					.add(param3.toString())
					.add(param4.toString())
					.toString();
			}
		};
	}

	static <T1, T2, T3, T4, T5> Tuple5<T1, T2, T3, T4, T5> tuple(
		final T1 param1, final T2 param2, final T3 param3,
		final T4 param4, final T5 param5
	) {
		return new Tuple5<>() {
			@Override
			public <T> T map(final FN5<T, T1, T2, T3, T4, T5> mapper) {
				return mapper.apply(param1, param2, param3, param4, param5);
			}

			@Override
			public boolean equals(final Object obj) {
				if (this == obj) {
					return true;
				}

				if (obj instanceof Tuple5<?, ?, ?, ?, ?>) {
					var tuple5 = (Tuple5<?, ?, ?, ?, ?>) obj;

					return tuple5.map((v1, v2, v3, v4, v5) -> Objects.equals(v1, param1)
						&& Objects.equals(v2, param2)
						&& Objects.equals(v3, param3)
						&& Objects.equals(v4, param4)
						&& Objects.equals(v5, param5));
				}
				return false;
			}

			@Override
			public int hashCode() {
				return Objects.hash(param1, param2, param3, param4, param5);
			}

			@Override
			public String toString() {
				return new StringJoiner(", ", "Tuple(", ")")
					.add(param1.toString())
					.add(param2.toString())
					.add(param3.toString())
					.add(param4.toString())
					.add(param5.toString())
					.toString();
			}
		};
	}

	static <T1, T2, T3, T4, T5, T6> Tuple6<T1, T2, T3, T4, T5, T6> tuple(
		final T1 param1,
		final T2 param2,
		final T3 param3,
		final T4 param4,
		final T5 param5,
		final T6 param6
	) {
		return new Tuple6<>() {
			@Override
			public <T> T map(final FN6<T, T1, T2, T3, T4, T5, T6> mapper) {
				return mapper.apply(param1, param2, param3, param4, param5, param6);
			}

			@Override
			public boolean equals(final Object obj) {
				if (this == obj) {
					return true;
				}

				if (obj instanceof Tuple6<?, ?, ?, ?, ?, ?>) {
					var tuple6 = (Tuple6<?, ?, ?, ?, ?, ?>) obj;

					return tuple6.map((v1, v2, v3, v4, v5, v6) -> Objects.equals(v1, param1)
						&& Objects.equals(v2, param2)
						&& Objects.equals(v3, param3)
						&& Objects.equals(v4, param4)
						&& Objects.equals(v5, param5)
						&& Objects.equals(v6, param6));
				}
				return false;
			}

			@Override
			public int hashCode() {
				return Objects.hash(param1, param2, param3, param4, param5, param6);
			}

			@Override
			public String toString() {
				return new StringJoiner(", ", "Tuple(", ")")
					.add(param1.toString())
					.add(param2.toString())
					.add(param3.toString())
					.add(param4.toString())
					.add(param5.toString())
					.add(param6.toString())
					.toString();
			}
		};
	}

	static <T1, T2, T3, T4, T5, T6, T7> Tuple7<T1, T2, T3, T4, T5, T6, T7> tuple(
		final T1 param1,
		final T2 param2,
		final T3 param3,
		final T4 param4,
		final T5 param5,
		final T6 param6,
		final T7 param7
	) {
		return new Tuple7<>() {
			@Override
			public <T> T map(final FN7<T, T1, T2, T3, T4, T5, T6, T7> mapper) {
				return mapper.apply(param1, param2, param3, param4, param5, param6, param7);
			}

			@Override
			public boolean equals(final Object obj) {
				if (this == obj) {
					return true;
				}

				if (obj instanceof Tuple7<?, ?, ?, ?, ?, ?, ?>) {
					var tuple7 = (Tuple7<?, ?, ?, ?, ?, ?, ?>) obj;

					return tuple7.map((v1, v2, v3, v4, v5, v6, v7) -> Objects.equals(v1, param1)
						&& Objects.equals(v2, param2)
						&& Objects.equals(v3, param3)
						&& Objects.equals(v4, param4)
						&& Objects.equals(v5, param5)
						&& Objects.equals(v6, param6)
						&& Objects.equals(v7, param7));
				}
				return false;
			}

			@Override
			public int hashCode() {
				return Objects.hash(param1, param2, param3, param4, param5, param6, param7);
			}

			@Override
			public String toString() {
				return new StringJoiner(", ", "Tuple(", ")")
					.add(param1.toString())
					.add(param2.toString())
					.add(param3.toString())
					.add(param4.toString())
					.add(param5.toString())
					.add(param6.toString())
					.add(param7.toString())
					.toString();
			}
		};
	}

	static <T1, T2, T3, T4, T5, T6, T7, T8> Tuple8<T1, T2, T3, T4, T5, T6, T7, T8> tuple(
		final T1 param1,
		final T2 param2,
		final T3 param3,
		final T4 param4,
		final T5 param5,
		final T6 param6,
		final T7 param7,
		final T8 param8
	) {
		return new Tuple8<>() {
			@Override
			public <T> T map(final FN8<T, T1, T2, T3, T4, T5, T6, T7, T8> mapper) {
				return mapper.apply(param1, param2, param3, param4, param5, param6, param7, param8);
			}

			@Override
			public boolean equals(final Object obj) {
				if (this == obj) {
					return true;
				}

				if (obj instanceof Tuple8<?, ?, ?, ?, ?, ?, ?, ?>) {
					var tuple8 = (Tuple8<?, ?, ?, ?, ?, ?, ?, ?>) obj;

					return tuple8.map((v1, v2, v3, v4, v5, v6, v7, v8) -> Objects.equals(v1, param1)
						&& Objects.equals(v2, param2)
						&& Objects.equals(v3, param3)
						&& Objects.equals(v4, param4)
						&& Objects.equals(v5, param5)
						&& Objects.equals(v6, param6)
						&& Objects.equals(v7, param7)
						&& Objects.equals(v8, param8));
				}
				return false;
			}

			@Override
			public int hashCode() {
				return Objects.hash(param1, param2, param3, param4, param5, param6, param7, param8);
			}

			@Override
			public String toString() {
				return new StringJoiner(", ", "Tuple(", ")")
					.add(param1.toString())
					.add(param2.toString())
					.add(param3.toString())
					.add(param4.toString())
					.add(param5.toString())
					.add(param6.toString())
					.add(param7.toString())
					.add(param8.toString())
					.toString();
			}
		};
	}

	static <T1, T2, T3, T4, T5, T6, T7, T8, T9> Tuple9<T1, T2, T3, T4, T5, T6, T7, T8, T9> tuple(
		final T1 param1,
		final T2 param2,
		final T3 param3,
		final T4 param4,
		final T5 param5,
		final T6 param6,
		final T7 param7,
		final T8 param8,
		final T9 param9
	) {
		return new Tuple9<>() {
			@Override
			public <T> T map(final FN9<T, T1, T2, T3, T4, T5, T6, T7, T8, T9> mapper) {
				return mapper.apply(param1, param2, param3, param4, param5, param6, param7, param8, param9);
			}

			@Override
			public boolean equals(final Object obj) {
				if (this == obj) {
					return true;
				}

				if (obj instanceof Tuple9<?, ?, ?, ?, ?, ?, ?, ?, ?>) {
					var tuple9 = (Tuple9<?, ?, ?, ?, ?, ?, ?, ?, ?>) obj;

					return tuple9.map((v1, v2, v3, v4, v5, v6, v7, v8, v9) -> Objects.equals(v1, param1)
						&& Objects.equals(v2, param2)
						&& Objects.equals(v3, param3)
						&& Objects.equals(v4, param4)
						&& Objects.equals(v5, param5)
						&& Objects.equals(v6, param6)
						&& Objects.equals(v7, param7)
						&& Objects.equals(v8, param8)
						&& Objects.equals(v9, param9));
				}
				return false;
			}

			@Override
			public int hashCode() {
				return Objects.hash(param1, param2, param3, param4, param5, param6, param7, param8, param9);
			}

			@Override
			public String toString() {
				return new StringJoiner(", ", "Tuple(", ")")
					.add(param1.toString())
					.add(param2.toString())
					.add(param3.toString())
					.add(param4.toString())
					.add(param5.toString())
					.add(param6.toString())
					.add(param7.toString())
					.add(param8.toString())
					.add(param9.toString())
					.toString();
			}
		};
	}
}
