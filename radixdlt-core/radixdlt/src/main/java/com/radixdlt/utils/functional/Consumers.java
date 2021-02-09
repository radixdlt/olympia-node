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
 * Collection of consumers for 1-9 arguments.
 */
public interface Consumers {
	@FunctionalInterface
	interface Consumer1<T1> {
		void accept(T1 param1);
	}

	@FunctionalInterface
	interface Consumer2<T1, T2> {
		void accept(T1 param1, T2 param2);
	}

	@FunctionalInterface
	interface Consumer3<T1, T2, T3> {
		void accept(T1 param1, T2 param2, T3 param3);
	}

	@FunctionalInterface
	interface Consumer4<T1, T2, T3, T4> {
		void accept(T1 param1, T2 param2, T3 param3, T4 param4);
	}

	@FunctionalInterface
	interface Consumer5<T1, T2, T3, T4, T5> {
		void accept(T1 param1, T2 param2, T3 param3, T4 param4, T5 param5);
	}

	@FunctionalInterface
	interface Consumer6<T1, T2, T3, T4, T5, T6> {
		void accept(T1 param1, T2 param2, T3 param3, T4 param4, T5 param5, T6 param6);
	}

	@FunctionalInterface
	interface Consumer7<T1, T2, T3, T4, T5, T6, T7> {
		void accept(T1 param1, T2 param2, T3 param3, T4 param4, T5 param5, T6 param6, T7 param7);
	}

	@FunctionalInterface
	interface Consumer8<T1, T2, T3, T4, T5, T6, T7, T8> {
		void accept(T1 param1, T2 param2, T3 param3, T4 param4, T5 param5, T6 param6, T7 param7, T8 param8);
	}

	@FunctionalInterface
	interface Consumer9<T1, T2, T3, T4, T5, T6, T7, T8, T9> {
		void accept(T1 param1, T2 param2, T3 param3, T4 param4, T5 param5, T6 param6, T7 param7, T8 param8, T9 param9);
	}
}
