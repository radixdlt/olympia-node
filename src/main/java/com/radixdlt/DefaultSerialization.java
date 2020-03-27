/*
 *
 *  * (C) Copyright 2020 Radix DLT Ltd
 *  *
 *  * Radix DLT Ltd licenses this file to you under the Apache License,
 *  * Version 2.0 (the "License"); you may not use this file except in
 *  * compliance with the License.  You may obtain a copy of the
 *  * License at
 *  *
 *  *  http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing,
 *  * software distributed under the License is distributed on an
 *  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  * either express or implied.  See the License for the specific
 *  * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt;

import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.core.ClasspathScanningSerializationPolicy;
import com.radixdlt.serialization.core.ClasspathScanningSerializerIds;

public final class DefaultSerialization {

	private DefaultSerialization() {
		throw new IllegalStateException("Can't construct");
	}

	private static class LazyHolder {
		static final Serialization INSTANCE = Serialization.create(
				ClasspathScanningSerializerIds.create(),
				ClasspathScanningSerializationPolicy.create()
		);
	}

	/**
	 * A singleton created using {@link ClasspathScanningSerializerIds} and {@link ClasspathScanningSerializationPolicy}
	 * @return A singleton created using {@link ClasspathScanningSerializerIds} and {@link ClasspathScanningSerializationPolicy},
	 */
	public static Serialization getInstance() {
		return LazyHolder.INSTANCE;
	}
}
