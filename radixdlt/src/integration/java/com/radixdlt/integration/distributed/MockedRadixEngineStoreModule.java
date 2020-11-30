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

package com.radixdlt.integration.distributed;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;

public class MockedRadixEngineStoreModule extends AbstractModule {
	@Override
	public void configure() {
		bind(Serialization.class).toInstance(DefaultSerialization.getInstance());
		bind(Integer.class).annotatedWith(Names.named("magic")).toInstance(1);
	}

	@Provides
	@Singleton
	private EngineStore<LedgerAtom> engineStore(Hasher hasher) {
		InMemoryEngineStore<LedgerAtom> inMemoryEngineStore = new InMemoryEngineStore<>();
		final ClientAtom genesisAtom = ClientAtom.create(
			ImmutableList.of(
				CMMicroInstruction.checkSpinAndPush(new SystemParticle(0, 0, 0), Spin.UP),
				CMMicroInstruction.checkSpinAndPush(new SystemParticle(1, 0, 0), Spin.NEUTRAL)
			),
			hasher
		);
		inMemoryEngineStore.storeAtom(genesisAtom);
		return inMemoryEngineStore;
	}

}
