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

import com.radixdlt.atom.TxActionListBuilder;
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.atommodel.tokens.StakingConstraintScrypt;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import com.radixdlt.client.api.ActionType;

import com.radixdlt.atom.MutableTokenDefinition;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.atommodel.tokens.TokensConstraintScrypt;
import com.radixdlt.atommodel.validators.ValidatorConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.client.store.ActionEntry;
import com.radixdlt.client.store.TransactionParser;
import com.radixdlt.client.api.TxHistoryEntry;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.identifiers.Rri;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.utils.UInt256;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class TransactionParserTest {
	private static final byte MAGIC = (byte) 0;

	private final ECKeyPair tokenOwnerKeyPair = ECKeyPair.generateNew();
	private final RadixAddress tokenOwnerAddress = new RadixAddress(MAGIC, tokenOwnerKeyPair.getPublicKey());
	private final ECKeyPair validatorKeyPair = ECKeyPair.generateNew();
	private final RadixAddress validatorAddress = new RadixAddress(MAGIC, validatorKeyPair.getPublicKey());

	private final RadixAddress otherAddress = new RadixAddress(MAGIC, ECKeyPair.generateNew().getPublicKey());
	private final EngineStore<Void> store = new InMemoryEngineStore<>();

	private final Rri tokenRri = Rri.ofSystem("xrd");
	private final MutableTokenDefinition tokDef = new MutableTokenDefinition(
		"xrd", "Test", "description", null, null
	);

	private final Rri tokenRriII = Rri.of(tokenOwnerKeyPair.getPublicKey(), "tst");
	private final MutableTokenDefinition tokDefII = new MutableTokenDefinition(
		"tst", "Test2", "description2", null, null
	);

	private RadixEngine<Void> engine;

	private TransactionParser parser = new TransactionParser(tokenRri, 0);

	@Before
	public void setup() throws Exception {
		final var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new ValidatorConstraintScrypt());
		cmAtomOS.load(new TokensConstraintScrypt());
		cmAtomOS.load(new StakingConstraintScrypt());

		final var cm = new ConstraintMachine.Builder()
			.setVirtualStoreLayer(cmAtomOS.virtualizedUpParticles())
			.setParticleStaticCheck(cmAtomOS.buildParticleStaticCheck())
			.setParticleTransitionProcedures(cmAtomOS.buildTransitionProcedures())
			.build();

		engine = new RadixEngine<>(cm, store);

		var txn0 = engine.construct(
			TxActionListBuilder.create()
				.createMutableToken(tokDef)
				.mint(this.tokenRri, this.tokenOwnerAddress, UInt256.TEN)
				.build()
		).buildWithoutSignature();
		var validatorBuilder = this.engine.construct(this.validatorAddress, new RegisterValidator());
		var txn1 = validatorBuilder.signAndBuild(this.validatorKeyPair::sign);

		engine.execute(List.of(txn0, txn1), null, PermissionLevel.SYSTEM);
	}

	@Test
	public void stakeIsParsedCorrectly() throws Exception {
		var txn = engine.construct(tokenOwnerAddress, nativeStake())
			.burn(tokenRri, UInt256.TWO)
			.signAndBuild(tokenOwnerKeyPair::sign);

		executeAndDecode(List.of(ActionType.STAKE, ActionType.BURN), UInt256.TWO, txn);
	}

	@Test
	public void unstakeIsParsedCorrectly() throws Exception {
		var txn1 = engine.construct(tokenOwnerAddress, nativeStake())
			.signAndBuild(tokenOwnerKeyPair::sign);
		engine.execute(List.of(txn1));

		var txn2 = engine.construct(tokenOwnerAddress, nativeUnstake())
			.burn(tokenRri, UInt256.FOUR)
			.signAndBuild(tokenOwnerKeyPair::sign);

		executeAndDecode(List.of(ActionType.UNSTAKE, ActionType.BURN), UInt256.FOUR, txn2);
	}

	@Test
	public void transferIsParsedCorrectly() throws Exception {
		//Use different token
		var txn = engine.construct(tokenOwnerAddress, TxActionListBuilder.create()
			.createMutableToken(tokDefII)
			.mint(tokenRriII, tokenOwnerAddress, UInt256.TEN)
			.transfer(tokenRriII, otherAddress, UInt256.FIVE)
			.burn(tokenRri, UInt256.FOUR)
			.build()
		).signAndBuild(tokenOwnerKeyPair::sign);

		executeAndDecode(List.of(ActionType.UNKNOWN, ActionType.UNKNOWN, ActionType.TRANSFER, ActionType.BURN), UInt256.FOUR, txn);
	}

	private void executeAndDecode(List<ActionType> expectedActions, UInt256 fee, Txn... txns) throws Exception {
		var list = engine.execute(List.of(txns), null, PermissionLevel.USER);

		if (txns.length != 1) {
			return;
		}

		var timestamp = Instant.ofEpochMilli(Instant.now().toEpochMilli());

		list.stream()
			.map(txn -> parser.parse(txn, timestamp))
			.forEach(entry -> entry
				.onFailureDo(Assert::fail)
				.onSuccess(historyEntry -> assertEquals(fee, historyEntry.getFee()))
				.map(this::toActionTypes)
				.onSuccess(types -> assertEquals(expectedActions, types))
			);
	}

	private StakeTokens nativeStake() {
		return new StakeTokens(validatorKeyPair.getPublicKey(), UInt256.FIVE);
	}

	private UnstakeTokens nativeUnstake() {
		return new UnstakeTokens(validatorKeyPair.getPublicKey(), UInt256.FIVE);
	}

	private List<ActionType> toActionTypes(TxHistoryEntry txEntry) {
		return txEntry.getActions()
			.stream()
			.map(ActionEntry::getType)
			.collect(Collectors.toList());
	}
}
