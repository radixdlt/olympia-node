/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.OptionalBinder;
import com.radixdlt.consensus.bft.PersistentVertexStore;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.store.berkeley.SerializedVertexStoreState;

import java.util.Optional;

public class MockedPersistenceStoreModule extends AbstractModule {

	@Override
	public void configure() {
		bind(PersistentSafetyStateStore.class).to(MockedPersistenceStore.class);
		bind(PersistentVertexStore.class).to(MockedPersistentVertexStore.class);
		OptionalBinder.newOptionalBinder(binder(), SerializedVertexStoreState.class);
	}

	private static class MockedPersistenceStore implements PersistentSafetyStateStore {
		@Override
		public Optional<SafetyState> get() {
			return Optional.empty();
		}

		@Override
		public void commitState(SafetyState safetyState) {
			// Nothing to do here
		}

		@Override
		public void close() {
			// Nothing to do here
		}
	}

	private static class MockedPersistentVertexStore implements PersistentVertexStore {
		@Override
		public void save(VerifiedVertexStoreState vertexStoreState) {
			// Nothing to do here
		}
	}
}
