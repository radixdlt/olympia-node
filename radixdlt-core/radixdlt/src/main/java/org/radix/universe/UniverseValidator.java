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

package org.radix.universe;

import com.radixdlt.crypto.Hasher;
import com.radixdlt.universe.Universe;

public final class UniverseValidator {
	private UniverseValidator() { }

	public static void validate(Universe universe, Hasher hasher) {
		// Check signature
		if (!universe.getCreator().verify(hasher.hash(universe), universe.getSignature())) {
			throw new IllegalStateException("Invalid universe signature");
		}
	}

	//This needs to be removed
	public static void dupvalidate(Universe universe, Hasher hasher) {
		// Check signature
		if (!universe.getCreator().verify(hasher.hash(universe), universe.getSignature())) {
			throw new IllegalStateException("Invalid universe signature");
		}
	}
	public static void dupvalidate1(Universe universe, Hasher hasher) {
		// Check signature
		if (!universe.getCreator().verify(hasher.hash(universe), universe.getSignature())) {
			throw new IllegalStateException("Invalid universe signature");
		}
	}
	public static void dupvalidate2(Universe universe, Hasher hasher) {
		// Check signature
		if (!universe.getCreator().verify(hasher.hash(universe), universe.getSignature())) {
			throw new IllegalStateException("Invalid universe signature");
		}
	}
}
