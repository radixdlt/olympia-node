/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.store.mvstore;

import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.counters.SystemCounters;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.radix.serialization.RadixTest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static com.radixdlt.store.mvstore.DatabaseEnvironment.BYTE_ARRAY_TYPE;
import static com.radixdlt.utils.SerializerTestDataGenerator.randomView;
import static com.radixdlt.utils.SerializerTestDataGenerator.randomVote;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class MVStoreSafetyStateStoreTest  extends RadixTest {
	private static final Vote UNUSED_VOTE = randomVote();

	private MVStoreSafetyStateStore stateStore;
	private DatabaseEnvironment dbEnv;

	@Before
	public void setUp() {
		initDB();
		// clear store before test
		dbEnv.inTransaction(tx -> {
			tx.openMap(MVStoreSafetyStateStore.NAME, BYTE_ARRAY_TYPE, BYTE_ARRAY_TYPE).clear();
			return Optional.of(true);
		});
	}

	private void initDB() {
		dbEnv = new DatabaseEnvironment(getProperties());
		dbEnv.start();

		stateStore = new MVStoreSafetyStateStore(getSerialization(), dbEnv, mock(SystemCounters.class));
	}

	@After
	public void tearDown() {
		stateStore.close();
		dbEnv.stop();
	}

	@Test
	public void safetyStoreCanBeRestored() {
		final var safetyState = new SafetyState(randomView(), Optional.of(randomVote()));
		stateStore.commitState(safetyState);

		tearDown();
		// only init, don't clear
		initDB();

		stateStore.getLastState()
			.ifPresentOrElse(
				state -> assertEquals(safetyState, state),
				() -> fail("Saved state not found")
			);
	}

	@Test
	public void latestStateIsRestored() {
		var states = new ArrayList<>(List.of(new SafetyState(randomView(), Optional.of(randomVote())),
											 new SafetyState(randomView(), Optional.of(randomVote())),
											 new SafetyState(randomView(), Optional.of(randomVote()))
		));

		states.forEach(stateStore::commitState);
		states.sort(Comparator.comparing(MVStoreSafetyStateStoreTest::getEpoch));

		tearDown();
		// only init, don't clear
		initDB();

		stateStore.getLastState()
			.ifPresentOrElse(
				state -> assertEquals(states.get(2), state),
				() -> fail("Saved state not found")
			);
	}

	private static long getEpoch(SafetyState state) {
		//UNUSED_VOTE is never used, it's necessary to tame Sonar on use of .get() without .isPresent()
		return state.getLastVote().orElse(UNUSED_VOTE).getEpoch();
	}
}