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
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.berkeley.BerkeleyAdditionalStore;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.sync.CommittedReader;

public class RadixEngineStoreModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(BerkeleyLedgerEntryStore.class).in(Scopes.SINGLETON);
		bind(new TypeLiteral<EngineStore<LedgerAndBFTProof>>() { })
			.to(BerkeleyLedgerEntryStore.class).in(Scopes.SINGLETON);
		bind(CommittedReader.class).to(BerkeleyLedgerEntryStore.class);
		Multibinder.newSetBinder(binder(), BerkeleyAdditionalStore.class);
	}
}
