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

package com.radixdlt.consensus.sync;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineErrorCode;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import com.radixdlt.network.addressbook.AddressBook;
import org.junit.Before;
import org.junit.Test;

public class SyncedRadixEngineTest {

	private RadixEngine<LedgerAtom> radixEngine;
	private SyncedRadixEngine stateSynchronizer;
	private CommittedAtomsStore committedAtomsStore;
	private AddressBook addressBook;
	private StateSyncNetwork stateSyncNetwork;

	@Before
	public void setup() {
		this.radixEngine = mock(RadixEngine.class);
		this.committedAtomsStore = mock(CommittedAtomsStore.class);
		this.addressBook = mock(AddressBook.class);
		this.stateSyncNetwork = mock(StateSyncNetwork.class);
		this.stateSynchronizer = new SyncedRadixEngine(
			radixEngine,
			committedAtomsStore,
			addressBook,
			stateSyncNetwork
		);
	}

	@Test
	public void when_insert_and_commit_vertex_with_engine_virtual_state_conflict__then_no_exception_should_be_thrown() throws RadixEngineException {
		CommittedAtom committedAtom = mock(CommittedAtom.class);
		when(committedAtom.getClientAtom()).thenReturn(mock(ClientAtom.class));
		when(committedAtom.getAID()).thenReturn(mock(AID.class));

		RadixEngineException e = mock(RadixEngineException.class);
		when(e.getErrorCode()).thenReturn(RadixEngineErrorCode.VIRTUAL_STATE_CONFLICT);
		when(e.getDataPointer()).thenReturn(DataPointer.ofAtom());
		doThrow(e).when(radixEngine).checkAndStore(eq(committedAtom));

		stateSynchronizer.execute(committedAtom);
		verify(radixEngine, times(1)).checkAndStore(eq(committedAtom));
	}


	@Test
	public void when_insert_and_commit_vertex_with_engine_state_conflict__then_no_exception_should_be_thrown() throws RadixEngineException {
		RadixEngineException e = mock(RadixEngineException.class);
		when(e.getErrorCode()).thenReturn(RadixEngineErrorCode.STATE_CONFLICT);
		when(e.getDataPointer()).thenReturn(DataPointer.ofAtom());

		LedgerAtom related = mock(LedgerAtom.class);
		when(related.getAID()).thenReturn(mock(AID.class));
		when(e.getRelated()).thenReturn(related);

		CommittedAtom committedAtom = mock(CommittedAtom.class);
		when(committedAtom.getAID()).thenReturn(mock(AID.class));
		doThrow(e).when(radixEngine).checkAndStore(eq(committedAtom));

		stateSynchronizer.execute(committedAtom);
		verify(radixEngine, times(1)).checkAndStore(eq(committedAtom));
	}

	@Test
	public void when_insert_and_commit_vertex_with_engine_missing_dependency__then_no_exception_should_be_thrown() throws RadixEngineException {
		RadixEngineException e = mock(RadixEngineException.class);
		when(e.getErrorCode()).thenReturn(RadixEngineErrorCode.MISSING_DEPENDENCY);
		when(e.getDataPointer()).thenReturn(DataPointer.ofAtom());
		CommittedAtom committedAtom = mock(CommittedAtom.class);
		doThrow(e).when(radixEngine).checkAndStore(eq(committedAtom));

		stateSynchronizer.execute(committedAtom);
		verify(radixEngine, times(1)).checkAndStore(eq(committedAtom));
	}

}