/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.mempool;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.DispatcherModule;
import com.radixdlt.MockedCryptoModule;
import com.radixdlt.MockedKeyModule;
import com.radixdlt.ModuleRunner;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.StartProcessorOnRunner;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.environment.rx.RxEnvironmentModule;
import com.radixdlt.environment.rx.RxRemoteEnvironment;
import com.radixdlt.environment.Runners;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.ledger.LedgerAccumulatorVerifier;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.utils.TimeSupplier;
import com.radixdlt.store.LastProof;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.Test;

import java.util.Comparator;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.mock;

public final class MempoolRunnerTest {
	@Inject
	private Map<String, ModuleRunner> moduleRunners;
	@Inject
	private EventDispatcher<MempoolAdd> mempoolAddEventDispatcher;

	private StateComputer stateComputer = mock(StateComputer.class);

	public Module createModule() {
		return new AbstractModule() {
			@Override
			public void configure() {
				bind(BFTNode.class).annotatedWith(Self.class).toInstance(mock(BFTNode.class));
				bind(LedgerProof.class).annotatedWith(LastProof.class)
					.toInstance(mock(LedgerProof.class));
				bind(StateComputer.class).toInstance(stateComputer);
				bind(SystemCounters.class).toInstance(new SystemCountersImpl());
				bind(RxRemoteEnvironment.class).toInstance(new RxRemoteEnvironment() {
					@Override
					public <T> Flowable<RemoteEvent<T>> remoteEvents(Class<T> remoteEventClass) {
						return Flowable.never();
					}
				});
				bind(LedgerAccumulator.class).toInstance(mock(LedgerAccumulator.class));
				bind(LedgerAccumulatorVerifier.class).toInstance(mock(LedgerAccumulatorVerifier.class));
				bind(new TypeLiteral<Comparator<LedgerProof>>() { }).toInstance(mock(Comparator.class));
				bind(TimeSupplier.class).toInstance(System::currentTimeMillis);
				Multibinder.newSetBinder(binder(), StartProcessorOnRunner.class);
				install(MempoolConfig.asModule(100, 10));
				install(new MockedKeyModule());
				install(new MockedCryptoModule());
				install(new RxEnvironmentModule());
				install(new DispatcherModule());
				install(new MempoolReceiverModule());
			}
		};
	}

	@Test
	public void dispatched_mempool_add_arrives_at_state_computer() {
		Guice.createInjector(createModule()).injectMembers(this);
		moduleRunners.get(Runners.MEMPOOL).start();

		MempoolAdd mempoolAdd = MempoolAdd.create(Txn.create(new byte[0]));
		mempoolAddEventDispatcher.dispatch(mempoolAdd);

		verify(stateComputer, timeout(1000).times(1))
			.addToMempool(eq(mempoolAdd), isNull());
	}
}
