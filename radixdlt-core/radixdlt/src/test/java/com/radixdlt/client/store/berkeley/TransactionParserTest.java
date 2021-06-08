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

import com.radixdlt.atom.ActionConstructors;
import com.radixdlt.atom.TxActionListBuilder;
import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.CreateSystem;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.atom.actions.SystemNextEpoch;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.atommodel.system.construction.CreateSystemConstructorV2;
import com.radixdlt.atommodel.system.construction.NextEpochConstructorV2;
import com.radixdlt.atommodel.system.scrypt.SystemConstraintScryptV2;
import com.radixdlt.atommodel.system.state.ValidatorStake;
import com.radixdlt.atommodel.tokens.construction.BurnTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.MintTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.StakeTokensConstructorV2;
import com.radixdlt.atommodel.tokens.construction.TransferTokensConstructorV2;
import com.radixdlt.atommodel.tokens.construction.UnstakeTokensConstructorV2;
import com.radixdlt.atommodel.tokens.scrypt.StakingConstraintScryptV3;
import com.radixdlt.atommodel.tokens.scrypt.TokensConstraintScryptV2;
import com.radixdlt.atommodel.validators.construction.RegisterValidatorConstructor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import com.radixdlt.client.api.ActionType;

import com.radixdlt.atom.MutableTokenDefinition;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atommodel.validators.scrypt.ValidatorConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.client.api.ActionEntry;
import com.radixdlt.client.store.TransactionParser;
import com.radixdlt.client.api.TxHistoryEntry;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.utils.UInt256;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class TransactionParserTest {
	private final ECKeyPair tokenOwnerKeyPair = ECKeyPair.generateNew();
	private final REAddr tokenOwnerAcct = REAddr.ofPubKeyAccount(tokenOwnerKeyPair.getPublicKey());
	private final REAddr otherAccount = REAddr.ofPubKeyAccount(ECKeyPair.generateNew().getPublicKey());
	private final ECKeyPair validatorKeyPair = ECKeyPair.generateNew();
	private final EngineStore<Void> store = new InMemoryEngineStore<>();

	private final REAddr tokenRri = REAddr.ofNativeToken();
	private final MutableTokenDefinition tokDef = new MutableTokenDefinition(
		"xrd", "Test", "description", null, null
	);

	private final REAddr tokenRriII = REAddr.ofHashedKey(tokenOwnerKeyPair.getPublicKey(), "tst");
	private final MutableTokenDefinition tokDefII = new MutableTokenDefinition(
		"tst", "Test2", "description2", null, null
	);

	private RadixEngine<Void> engine;

	private final TransactionParser parser = new TransactionParser();

	@Before
	public void setup() throws Exception {
		final var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new SystemConstraintScryptV2());
		cmAtomOS.load(new ValidatorConstraintScrypt());
		cmAtomOS.load(new TokensConstraintScryptV2());
		cmAtomOS.load(new StakingConstraintScryptV3());

		final var cm = new ConstraintMachine.Builder()
			.setVirtualStoreLayer(cmAtomOS.virtualizedUpParticles())
			.setParticleStaticCheck(cmAtomOS.buildParticleStaticCheck())
			.setParticleTransitionProcedures(cmAtomOS.getProcedures())
			.build();

		var actionConstructors = ActionConstructors.newBuilder()
			.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
			.put(RegisterValidator.class, new RegisterValidatorConstructor())
			.put(MintToken.class, new MintTokenConstructor())
			.put(TransferToken.class, new TransferTokensConstructorV2())
			.put(BurnToken.class, new BurnTokenConstructor())
			.put(StakeTokens.class, new StakeTokensConstructorV2())
			.put(UnstakeTokens.class, new UnstakeTokensConstructorV2())
			.put(SystemNextEpoch.class, new NextEpochConstructorV2())
			.put(CreateSystem.class, new CreateSystemConstructorV2())
			.build();

		engine = new RadixEngine<>(actionConstructors, cm, store);

		var txn0 = engine.construct(
			TxActionListBuilder.create()
				.createMutableToken(tokDef)
				.mint(this.tokenRri, this.tokenOwnerAcct, ValidatorStake.MINIMUM_STAKE.multiply(UInt256.TWO))
				.build()
		).buildWithoutSignature();
		var validatorBuilder = this.engine.construct(
			List.of(new RegisterValidator(this.validatorKeyPair.getPublicKey()), new CreateSystem()));
		var txn1 = validatorBuilder.signAndBuild(this.validatorKeyPair::sign);

		engine.execute(List.of(txn0, txn1), null, PermissionLevel.SYSTEM);
	}

	@Test
	public void stakeIsParsedCorrectly() throws Exception {
		var txn = engine.construct(tokenOwnerKeyPair.getPublicKey(),
			List.of(nativeStake(), new BurnToken(tokenRri, tokenOwnerAcct, UInt256.TWO))
		)
			.signAndBuild(tokenOwnerKeyPair::sign);

		executeAndDecode(List.of(ActionType.STAKE), UInt256.TWO, txn);
	}

	@Test
	public void unstakeIsParsedCorrectly() throws Exception {
		var txn1 = engine.construct(tokenOwnerKeyPair.getPublicKey(), nativeStake())
			.signAndBuild(tokenOwnerKeyPair::sign);
		engine.execute(List.of(txn1));
		var nextEpoch = engine.construct(new SystemNextEpoch(s -> List.of(validatorKeyPair.getPublicKey()), 0))
			.buildWithoutSignature();
		engine.execute(List.of(nextEpoch), null, PermissionLevel.SYSTEM);

		var txn2 = engine.construct(tokenOwnerKeyPair.getPublicKey(),
			List.of(nativeUnstake(), new BurnToken(tokenRri, tokenOwnerAcct, UInt256.FOUR))
		)
			.signAndBuild(tokenOwnerKeyPair::sign);

		executeAndDecode(List.of(ActionType.UNSTAKE), UInt256.FOUR, txn2);
	}

	@Test
	public void transferIsParsedCorrectly() throws Exception {
		//Use different token
		var txn = engine.construct(tokenOwnerKeyPair.getPublicKey(), TxActionListBuilder.create()
			.createMutableToken(tokDefII)
			.mint(tokenRriII, tokenOwnerAcct, ValidatorStake.MINIMUM_STAKE.multiply(UInt256.TWO))
			.transfer(tokenRriII, tokenOwnerAcct, otherAccount, ValidatorStake.MINIMUM_STAKE)
			.burn(tokenRri, tokenOwnerAcct, UInt256.FOUR)
			.build()
		).signAndBuild(tokenOwnerKeyPair::sign);

		executeAndDecode(List.of(ActionType.UNKNOWN, ActionType.UNKNOWN, ActionType.TRANSFER), UInt256.FOUR, txn);
	}

	private void executeAndDecode(List<ActionType> expectedActions, UInt256 fee, Txn... txns) throws Exception {
		var list = engine.execute(List.of(txns), null, PermissionLevel.USER);

		if (txns.length != 1) {
			return;
		}

		var timestamp = Instant.ofEpochMilli(Instant.now().toEpochMilli());

		list.stream()
			.map(txn -> parser.parse(txn, timestamp, addr -> addr.toString(), (k, a) -> a))
			.forEach(entry -> entry
				.onFailureDo(Assert::fail)
				.onSuccess(historyEntry -> assertEquals(fee, historyEntry.getFee()))
				.map(this::toActionTypes)
				.onSuccess(types -> assertEquals(expectedActions, types))
			);
	}

	private StakeTokens nativeStake() {
		return new StakeTokens(tokenOwnerAcct, validatorKeyPair.getPublicKey(), ValidatorStake.MINIMUM_STAKE);
	}

	private UnstakeTokens nativeUnstake() {
		return new UnstakeTokens(tokenOwnerAcct, validatorKeyPair.getPublicKey(), ValidatorStake.MINIMUM_STAKE);
	}

	private List<ActionType> toActionTypes(TxHistoryEntry txEntry) {
		return txEntry.getActions()
			.stream()
			.map(ActionEntry::getType)
			.collect(Collectors.toList());
	}
}
