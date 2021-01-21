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

package com.radixdlt.constraintmachine;

import java.util.Set;

import com.radixdlt.identifiers.EUID;

public final class VoidParticle extends Particle {
	private VoidParticle() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	@Override
	public Set<EUID> getDestinations() {
		throw new IllegalStateException("No destinations for VoidParticle");
	}
}
