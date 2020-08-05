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

package com.radixdlt.middleware2;

import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.api.StoredAtom;
import com.radixdlt.api.StoredFailure;
import com.radixdlt.consensus.CommittedStateSync;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.engine.RadixEngineException;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;

public class InternalMessagePasserTest {
	@Test
	public void when_send_committed_state_sync_event__then_should_receive_it() {
		InternalMessagePasser internalMessagePasser = new InternalMessagePasser();
		TestObserver<CommittedStateSync> testObserver = internalMessagePasser.committedStateSyncs().test();
		long stateVersion = 12345;
		Object opaque = mock(Object.class);
		internalMessagePasser.sendCommittedStateSync(stateVersion, opaque);
		testObserver.awaitCount(1);
		testObserver.assertValue(s -> s.getOpaque().equals(opaque) && s.getStateVersion() == stateVersion);
		testObserver.assertNotComplete();
	}

	@Test
	public void when_send_epoch_change_event__then_should_receive_it() {
		InternalMessagePasser internalMessagePasser = new InternalMessagePasser();
		TestObserver<EpochChange> testObserver = internalMessagePasser.epochChanges().test();
		EpochChange epochChange = mock(EpochChange.class);
		internalMessagePasser.epochChange(epochChange);
		testObserver.awaitCount(1);
		testObserver.assertValue(epochChange);
		testObserver.assertNotComplete();
	}

	@Test
	public void when_send_stored_event__then_should_receive_it() {
		InternalMessagePasser internalMessagePasser = new InternalMessagePasser();
		TestObserver<StoredAtom> testObserver = internalMessagePasser.storedAtoms().test();
		CommittedAtom committedAtom = mock(CommittedAtom.class);
		internalMessagePasser.sendStored(committedAtom, ImmutableSet.of());
		testObserver.awaitCount(1);
		testObserver.assertValueAt(0, s -> s.getAtom().equals(committedAtom) && s.getDestinations().isEmpty());
		testObserver.assertNotComplete();
	}

	@Test
	public void when_send_stored_exception_event__then_should_receive_it() {
		InternalMessagePasser internalMessagePasser = new InternalMessagePasser();
		TestObserver<StoredFailure> testObserver = internalMessagePasser.storedExceptions().test();
		CommittedAtom committedAtom = mock(CommittedAtom.class);
		RadixEngineException e = mock(RadixEngineException.class);
		internalMessagePasser.sendStoredFailure(committedAtom, e);
		testObserver.awaitCount(1);
		testObserver.assertValueAt(0, s -> s.getAtom().equals(committedAtom) && s.getException().equals(e));
		testObserver.assertNotComplete();
	}
}