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
package com.radixdlt.client.store.berkeley;

import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.constraintmachine.ParsedInstruction;
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.utils.UInt256;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import static com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;

public class BerkeleyClientApiStoreTest {
	private static final RadixAddress OWNER = RadixAddress.from("JEbhKQzBn4qJzWJFBbaPioA2GTeaQhuUjYWkanTE6N8VvvPpvM8");
	private static final RadixAddress DELEGATE = RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
	private static final RadixAddress TOKEN_ADDRESS = RadixAddress.from("23B6fH3FekJeP6e5guhZAk6n9z4fmTo5Tngo3a11Wg5R8gsWTV2x");
	private static final RRI TOKEN = RRI.of(TOKEN_ADDRESS, "COOKIE");
	private static final UInt256 GRANULARITY = UInt256.ONE;
	private static final ImmutableMap<TokenTransition, TokenPermission> TOKEN_PERMISSIONS = ImmutableMap.of();

	private final Serialization serialization = DefaultSerialization.getInstance();
	private final BerkeleyLedgerEntryStore ledgerStore = mock(BerkeleyLedgerEntryStore.class);

	private DatabaseEnvironment environment;

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Before
	public void setUp() {
		environment = new DatabaseEnvironment(folder.getRoot().getAbsolutePath(), 0);
	}

	@Test
	public void tokenBalancesAreReturned() {
		var particles = List.of(
			ParsedInstruction.up(stake(UInt256.TWO)),
			ParsedInstruction.up(stake(UInt256.FIVE)),
			ParsedInstruction.up(transfer(UInt256.NINE)),
			ParsedInstruction.up(transfer(UInt256.ONE)),
			ParsedInstruction.down(transfer(UInt256.ONE))
		);
		var clientApiStore = prepareApiStore(particles);

		clientApiStore.getTokenBalances(OWNER)
			.onSuccess(list -> {
				assertEquals(1, list.size());
				assertEquals(UInt256.NINE, list.get(0).getAmount());
				assertEquals(TOKEN, list.get(0).getRri());
			})
			.onFailureDo(() -> fail("Failure is not expected here"));
	}

	@SuppressWarnings("unchecked")
	private BerkeleyClientApiStore prepareApiStore(List<ParsedInstruction> particles) {
		//Insert necessary values on DB rebuild
		doAnswer(invocation -> {
			particles.forEach(invocation.<Consumer<ParsedInstruction>>getArgument(0));
			return null;
		}).when(ledgerStore).forEach(any(Consumer.class));

		return new BerkeleyClientApiStore(environment, ledgerStore, serialization, mock(ScheduledEventDispatcher.class));
	}

	private StakedTokensParticle stake(UInt256 amount) {
		return new StakedTokensParticle(DELEGATE, OWNER, amount, GRANULARITY, TOKEN, TOKEN_PERMISSIONS, 1L);
	}

	private TransferrableTokensParticle transfer(UInt256 amount) {
		return new TransferrableTokensParticle(OWNER, amount, GRANULARITY, TOKEN, TOKEN_PERMISSIONS, 1L);
	}
}