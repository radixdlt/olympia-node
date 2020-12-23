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

package com.radixdlt.test.utils;

import static org.mockito.Mockito.mock;

/**
 * Typed mock wrappers to avoid compiler warnings where they are not
 * warranted.
 */
public final class TypedMocks {

	private TypedMocks() {
		throw new IllegalStateException("Can't construct");
	}

	/**
	 * Runtime checked typed mock, primarily for types with type arguments.
	 *
	 * @param <T> The type of the mock without type arguments, eg {@code List}
	 * @param <U> The type of the mock with type arguments, eg {@code List<Long>}
	 * @param cls The raw class for the mocked type
	 * @return the mock, cast to type {@code U}.  If {@code U} is not assignable
	 * 		from {@code T}, then a {@code ClassCastException} will occur.
	 */
	public static <T, U> U rmock(Class<T> cls) {
		@SuppressWarnings("unchecked")
		U value = (U) mock(cls);
		return value;
	}
}
