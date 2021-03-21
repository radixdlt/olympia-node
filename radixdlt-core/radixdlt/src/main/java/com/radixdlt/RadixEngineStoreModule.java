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
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.radixdlt.atom.Atom;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import com.radixdlt.middleware2.store.RadixEngineAtomicCommitManager;
import com.radixdlt.store.EngineStore;
import com.radixdlt.sync.CommittedReader;

public class RadixEngineStoreModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(new TypeLiteral<EngineStore<Atom, LedgerProof>>() { })
			.to(CommittedAtomsStore.class).in(Scopes.SINGLETON);
		bind(RadixEngineAtomicCommitManager.class).to(CommittedAtomsStore.class);
		bind(CommittedReader.class).to(CommittedAtomsStore.class);
		bind(CommittedAtomsStore.class).in(Scopes.SINGLETON);
	}
}
