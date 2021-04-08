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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.radix.api.jsonrpc.ActionType;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.MutableTokenDefinition;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.actions.StakeNativeToken;
import com.radixdlt.atom.actions.UnstakeNativeToken;
import com.radixdlt.atommodel.tokens.TokensConstraintScrypt;
import com.radixdlt.atommodel.validators.ValidatorConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.client.store.ActionEntry;
import com.radixdlt.client.store.ParsedTx;
import com.radixdlt.client.store.ParticleWithSpin;
import com.radixdlt.client.store.TransactionParser;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.RETxn;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

import static com.radixdlt.client.ClientApiUtils.extractCreator;
import static com.radixdlt.serialization.SerializationUtils.restore;

public class TransactionParserTest {
	private static final byte MAGIC = (byte) 0;

	private final ECKeyPair tokenOwnerKeyPair = ECKeyPair.generateNew();
	private final RadixAddress tokenOwnerAddress = new RadixAddress(MAGIC, tokenOwnerKeyPair.getPublicKey());
	private final ECKeyPair validatorKeyPair = ECKeyPair.generateNew();
	private final RadixAddress validatorAddress = new RadixAddress(MAGIC, validatorKeyPair.getPublicKey());

	private final RadixAddress otherAddress = new RadixAddress(MAGIC, ECKeyPair.generateNew().getPublicKey());
	private final EngineStore<Void> store = new InMemoryEngineStore<>();

	private final RRI tokenRri = RRI.of(tokenOwnerAddress, "TEST");
	private final MutableTokenDefinition tokDef = new MutableTokenDefinition(
		"TEST", "Test", "description", null, null
	);

	private final RRI tokenRriII = RRI.of(tokenOwnerAddress, "TEST2");
	private final MutableTokenDefinition tokDefII = new MutableTokenDefinition(
		"TEST2", "Test2", "description2", null, null
	);

	private RadixEngine<Void> engine;

	@Before
	public void setup() throws Exception {
		final var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new ValidatorConstraintScrypt());
		cmAtomOS.load(new TokensConstraintScrypt());

		final var cm = new ConstraintMachine.Builder()
			.setVirtualStoreLayer(cmAtomOS.virtualizedUpParticles())
			.setParticleStaticCheck(cmAtomOS.buildParticleStaticCheck())
			.setParticleTransitionProcedures(cmAtomOS.buildTransitionProcedures())
			.build();

		engine = new RadixEngine<>(cm, store);

		var txn1 = TxBuilder.newBuilder(tokenOwnerAddress)
			.createMutableToken(tokDef)
			.mint(tokenRri, tokenOwnerAddress, UInt256.TEN)
			.signAndBuild(tokenOwnerKeyPair::sign);

		var txn2 = TxBuilder.newBuilder(validatorAddress)
			.registerAsValidator()
			.signAndBuild(validatorKeyPair::sign);

		engine.execute(List.of(txn1, txn2));
	}

	@Test
	public void stakeIsParsedCorrectly() throws Exception {
		var txn = engine.construct(tokenOwnerAddress, nativeStake())
			.burnForFee(tokenRri, UInt256.TWO)
			.signAndBuild(tokenOwnerKeyPair::sign);

		executeAndDecode(List.of(ActionType.STAKE), UInt256.TWO, txn);
	}

	@Test
	public void unstakeIsParsedCorrectly() throws Exception {
		var txn1 = engine.construct(tokenOwnerAddress, nativeStake())
			.signAndBuild(tokenOwnerKeyPair::sign);
		engine.execute(List.of(txn1));

		var txn2 = engine.construct(tokenOwnerAddress, nativeUnstake())
			.burnForFee(tokenRri, UInt256.FOUR)
			.signAndBuild(tokenOwnerKeyPair::sign);

		executeAndDecode(List.of(ActionType.UNSTAKE), UInt256.FOUR, txn2);
	}

	@Test
	public void transferIsParsedCorrectly() throws Exception {
		//Use different token
		var txn = engine.construct(tokenOwnerAddress, List.of())
			.createMutableToken(tokDefII)
			.mint(tokenRriII, tokenOwnerAddress, UInt256.TEN)
			.transfer(tokenRriII, otherAddress, UInt256.FIVE)
			.burnForFee(tokenRriII, UInt256.FOUR)
			.signAndBuild(tokenOwnerKeyPair::sign);

		executeAndDecode(List.of(ActionType.TRANSFER), UInt256.FOUR, txn);
	}

	private void executeAndDecode(
		List<ActionType> expectedActions, UInt256 fee, Txn... txns
	) throws RadixEngineException, InterruptedException {
		var list = engine.execute(List.of(txns), null, PermissionLevel.USER);

		if (txns.length != 1) {
			return;
		}

		var timestamp = Instant.ofEpochMilli(Instant.now().toEpochMilli());

		list.stream()
			.map(this::toParsedTx)
			.map(result -> result.flatMap(parsedTx -> TransactionParser.parse(tokenOwnerAddress, parsedTx, timestamp)))
			.forEach(entry -> entry
				.onFailureDo(Assert::fail)
				.onSuccess(historyEntry -> assertEquals(fee, historyEntry.getFee()))
				.map(this::toActionTypes)
				.onSuccess(types -> assertEquals(expectedActions, types)));
	}

	private StakeNativeToken nativeStake() {
		return new StakeNativeToken(tokenRri, validatorAddress, UInt256.FIVE);
	}

	private UnstakeNativeToken nativeUnstake() {
		return new UnstakeNativeToken(tokenRri, validatorAddress, UInt256.FIVE);
	}

	private Result<ParsedTx> toParsedTx(RETxn reTxn) {
		var particles = reTxn.instructions()
			.stream()
			.map(ParticleWithSpin::create)
			.collect(Collectors.toList());

		return restore(DefaultSerialization.getInstance(), reTxn.getTxn().getPayload(), Atom.class)
			.map(atom -> ParsedTx.create(
				reTxn.getTxn(),
				particles,
				Optional.empty(),
				extractCreator(atom, MAGIC)
			));
	}

	private List<ActionType> toActionTypes(com.radixdlt.client.store.TxHistoryEntry txEntry) {
		return txEntry.getActions()
			.stream()
			.map(ActionEntry::getType)
			.collect(Collectors.toList());
	}
}
