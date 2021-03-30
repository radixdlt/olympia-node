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

import com.radixdlt.atom.Substate;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.constraintmachine.ParsedInstruction;
import com.radixdlt.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.store.TokenDefinitionRecord;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.utils.UInt256;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;

public class BerkeleyClientApiStoreTest {
	private static final RadixAddress OWNER = RadixAddress.from("JEbhKQzBn4qJzWJFBbaPioA2GTeaQhuUjYWkanTE6N8VvvPpvM8");
	private static final RadixAddress DELEGATE = RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
	private static final RadixAddress TOKEN_ADDRESS = RadixAddress.from("23B6fH3FekJeP6e5guhZAk6n9z4fmTo5Tngo3a11Wg5R8gsWTV2x");
	private static final RRI TOKEN = RRI.of(TOKEN_ADDRESS, "COOKIE");
	private static final UInt256 GRANULARITY = UInt256.ONE;

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
			ParsedInstruction.up(Substate.create(stake(UInt256.TWO), mock(SubstateId.class))),
			ParsedInstruction.up(Substate.create(stake(UInt256.FIVE), mock(SubstateId.class))),
			ParsedInstruction.up(Substate.create(transfer(UInt256.NINE), mock(SubstateId.class))),
			ParsedInstruction.up(Substate.create(transfer(UInt256.ONE), mock(SubstateId.class))),
			ParsedInstruction.down(Substate.create(transfer(UInt256.ONE), mock(SubstateId.class)))
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

	@Test
	public void tokenSupplyIsCalculateProperlyForInitialTokenIssuance() {
		var particles = List.of(
			ParsedInstruction.up(Substate.create(emission(UInt256.MAX_VALUE), mock(SubstateId.class)))
		);
		var clientApiStore = prepareApiStore(particles);

		clientApiStore.getTokenSupply(TOKEN)
			.onFailureDo(Assert::fail)
			.onSuccess(amount -> assertEquals(UInt256.ZERO, amount));
	}

	@Test
	public void tokenSupplyIsCalculateProperlyAfterBurnMint() {
		var particles = List.of(
			ParsedInstruction.up(Substate.create(emission(UInt256.MAX_VALUE), mock(SubstateId.class))),
			ParsedInstruction.down(Substate.create(emission(UInt256.TWO), mock(SubstateId.class))),
			ParsedInstruction.down(Substate.create(emission(UInt256.FIVE), mock(SubstateId.class))),
			ParsedInstruction.up(Substate.create(emission(UInt256.ONE), mock(SubstateId.class)))
		);
		var clientApiStore = prepareApiStore(particles);

		clientApiStore.getTokenSupply(TOKEN)
			.onFailureDo(Assert::fail)
			.onSuccess(amount -> assertEquals(UInt256.SIX, amount));
	}

	@Test
	public void mutableTokenDefinitionIsStoredAndAccessible() {
		var fooToken = mutableTokenDef("FOO");
		var barToken = mutableTokenDef("BAR");
		var particles = List.of(
			ParsedInstruction.up(Substate.create(fooToken, mock(SubstateId.class))),
			ParsedInstruction.up(Substate.create(barToken, mock(SubstateId.class)))
		);
		var clientApiStore = prepareApiStore(particles);

		var fooDef = TokenDefinitionRecord.from(fooToken, UInt256.ZERO);
		var barDef = TokenDefinitionRecord.from(barToken, UInt256.ZERO);

		clientApiStore.getTokenDefinition(fooToken.getRRI())
			.onSuccess(tokenDef -> assertEquals(fooDef, tokenDef))
			.onFailureDo(Assert::fail);
		clientApiStore.getTokenDefinition(barToken.getRRI())
			.onSuccess(tokenDef -> assertEquals(barDef, tokenDef))
			.onFailureDo(Assert::fail);
	}

	@Test
	public void fixedTokenDefinitionIsStoredAndAccessible() {
		var fooToken = fixedTokenDef("FOO");
		var barToken = fixedTokenDef("BAR");
		var particles = List.of(
			ParsedInstruction.up(Substate.create(fooToken, mock(SubstateId.class))),
			ParsedInstruction.up(Substate.create(barToken, mock(SubstateId.class)))
		);
		var clientApiStore = prepareApiStore(particles);

		var fooDef = TokenDefinitionRecord.from(fooToken);
		var barDef = TokenDefinitionRecord.from(barToken);

		clientApiStore.getTokenDefinition(fooToken.getRRI())
			.onSuccess(tokenDef -> assertEquals(fooDef, tokenDef))
			.onFailureDo(Assert::fail);
		clientApiStore.getTokenDefinition(barToken.getRRI())
			.onSuccess(tokenDef -> assertEquals(barDef, tokenDef))
			.onFailureDo(Assert::fail);
	}

	@SuppressWarnings("unchecked")
	private BerkeleyClientApiStore prepareApiStore(List<ParsedInstruction> particles) {
		//Insert necessary values on DB rebuild
		doAnswer(invocation -> {
			particles.forEach(invocation.<Consumer<ParsedInstruction>>getArgument(0));
			return null;
		}).when(ledgerStore).forEach(any(Consumer.class));

		var ledgerCommitted = mock(Observable.class);
		when(ledgerCommitted.observeOn(any())).thenReturn(ledgerCommitted);

		var mock = mock(Disposable.class);
		when(ledgerCommitted.subscribe((io.reactivex.rxjava3.functions.Consumer) any())).thenReturn(mock);


		return new BerkeleyClientApiStore(
			environment,
			ledgerStore,
			serialization,
			mock(SystemCounters.class),
			mock(ScheduledEventDispatcher.class),
			ledgerCommitted
		);
	}

	private StakedTokensParticle stake(UInt256 amount) {
		return new StakedTokensParticle(DELEGATE, OWNER, amount, GRANULARITY, TOKEN, Map.of());
	}

	private UnallocatedTokensParticle emission(UInt256 amount) {
		return new UnallocatedTokensParticle(amount, GRANULARITY, TOKEN, Map.of());
	}

	private TransferrableTokensParticle transfer(UInt256 amount) {
		return new TransferrableTokensParticle(OWNER, amount, GRANULARITY, TOKEN, Map.of());
	}

	private MutableSupplyTokenDefinitionParticle mutableTokenDef(String symbol) {
		return new MutableSupplyTokenDefinitionParticle(
			RRI.of(TOKEN_ADDRESS, symbol),
			symbol,
			description(symbol),
			UInt256.ONE,
			iconUrl(symbol),
			homeUrl(symbol),
			Map.of(
				TokenTransition.MINT, TokenPermission.ALL,
				TokenTransition.BURN, TokenPermission.ALL
			)
		);
	}

	private FixedSupplyTokenDefinitionParticle fixedTokenDef(String symbol) {
		return new FixedSupplyTokenDefinitionParticle(
			RRI.of(TOKEN_ADDRESS, symbol),
			symbol,
			description(symbol),
			UInt256.TEN,
			UInt256.ONE,
			iconUrl(symbol),
			homeUrl(symbol)
		);
	}

	private String description(String symbol) {
		return "Token with symbol " + symbol;
	}

	private String iconUrl(String symbol) {
		return "https://" + symbol.toLowerCase(Locale.US) + ".coin.com/icon";
	}

	private String homeUrl(String symbol) {
		return "https://" + symbol.toLowerCase(Locale.US) + ".coin.com/home";
	}
}
