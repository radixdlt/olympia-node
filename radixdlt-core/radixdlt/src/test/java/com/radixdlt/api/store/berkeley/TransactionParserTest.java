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
package com.radixdlt.api.store.berkeley;

import com.radixdlt.accounting.REResourceAccounting;
import com.radixdlt.accounting.TwoActorEntry;
import com.radixdlt.atom.REConstructor;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.CreateSystem;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.FeeReservePut;
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.atom.actions.NextEpoch;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.atom.actions.UpdateAllowDelegationFlag;
import com.radixdlt.application.system.construction.CreateSystemConstructorV2;
import com.radixdlt.application.system.construction.NextEpochConstructorV3;
import com.radixdlt.application.system.construction.FeeReservePutConstructor;
import com.radixdlt.application.system.scrypt.EpochUpdateConstraintScrypt;
import com.radixdlt.application.system.scrypt.FeeConstraintScrypt;
import com.radixdlt.application.system.scrypt.RoundUpdateConstraintScrypt;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.application.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.application.tokens.construction.MintTokenConstructor;
import com.radixdlt.application.tokens.construction.StakeTokensConstructorV3;
import com.radixdlt.application.tokens.construction.TransferTokensConstructorV2;
import com.radixdlt.application.tokens.construction.UnstakeTokensConstructorV2;
import com.radixdlt.application.tokens.scrypt.StakingConstraintScryptV4;
import com.radixdlt.application.tokens.scrypt.TokensConstraintScryptV3;
import com.radixdlt.application.validators.construction.RegisterValidatorConstructor;
import com.radixdlt.application.validators.construction.UpdateAllowDelegationFlagConstructor;
import com.radixdlt.application.validators.scrypt.ValidatorConstraintScryptV2;
import com.radixdlt.application.validators.scrypt.ValidatorRegisterConstraintScrypt;
import com.radixdlt.constraintmachine.meter.FixedFeeMeter;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import com.radixdlt.api.data.ActionType;

import com.radixdlt.atom.MutableTokenDefinition;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.api.data.ActionEntry;
import com.radixdlt.api.store.TransactionParser;
import com.radixdlt.api.data.TxHistoryEntry;
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
		null, "xrd", "Test", "description", null, null
	);

	private final REAddr tokenRriII = REAddr.ofHashedKey(tokenOwnerKeyPair.getPublicKey(), "tst");
	private final MutableTokenDefinition tokDefII = new MutableTokenDefinition(
		tokenOwnerKeyPair.getPublicKey(), "tst", "Test2", "description2", null, null
	);
	private final Addressing addressing = Addressing.ofNetwork(Network.LOCALNET);

	private RadixEngine<Void> engine;

	private final TransactionParser parser = new TransactionParser(addressing);

	@Before
	public void setup() throws Exception {
		final var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new RoundUpdateConstraintScrypt(10));
		cmAtomOS.load(new EpochUpdateConstraintScrypt(
			10, Amount.ofTokens(10).toSubunits(), 9800, 1, 10
		));
		cmAtomOS.load(new ValidatorConstraintScryptV2(2));
		cmAtomOS.load(new TokensConstraintScryptV3());
		cmAtomOS.load(new StakingConstraintScryptV4(Amount.ofTokens(10).toSubunits()));
		cmAtomOS.load(new FeeConstraintScrypt());
		cmAtomOS.load(new ValidatorRegisterConstraintScrypt());

		final var cm = new ConstraintMachine(
			cmAtomOS.virtualizedUpParticles(),
			cmAtomOS.getProcedures(),
			FixedFeeMeter.create(UInt256.FOUR)
		);
		var parser = new REParser(cmAtomOS.buildSubstateDeserialization());
		var substateSerialization = cmAtomOS.buildSubstateSerialization();

		var actionConstructors = REConstructor.newBuilder()
			.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
			.put(RegisterValidator.class, new RegisterValidatorConstructor())
			.put(MintToken.class, new MintTokenConstructor())
			.put(TransferToken.class, new TransferTokensConstructorV2())
			.put(FeeReservePut.class, new FeeReservePutConstructor())
			.put(StakeTokens.class, new StakeTokensConstructorV3(Amount.ofTokens(10).toSubunits()))
			.put(UnstakeTokens.class, new UnstakeTokensConstructorV2())
			.put(NextEpoch.class, new NextEpochConstructorV3(Amount.ofTokens(10).toSubunits(), 9800, 1, 10))
			.put(CreateSystem.class, new CreateSystemConstructorV2())
			.put(UpdateAllowDelegationFlag.class, new UpdateAllowDelegationFlagConstructor())
			.build();

		engine = new RadixEngine<>(parser, substateSerialization, actionConstructors, cm, store);

		var txn0 = engine.construct(
			TxnConstructionRequest.create()
				.createMutableToken(tokDef)
				.mint(this.tokenRri, this.tokenOwnerAcct, Amount.ofTokens(10 * 2).toSubunits())
		).buildWithoutSignature();
		var validatorBuilder = this.engine.construct(
			TxnConstructionRequest.create()
				.action(new RegisterValidator(this.validatorKeyPair.getPublicKey()))
				.action(new UpdateAllowDelegationFlag(this.validatorKeyPair.getPublicKey(), true))
				.action(new CreateSystem(System.currentTimeMillis()))
		);
		var txn1 = validatorBuilder.buildWithoutSignature();

		engine.execute(List.of(txn0, txn1), null, PermissionLevel.SYSTEM);
	}

	@Test
	public void stakeIsParsedCorrectly() throws Exception {
		var txn = engine.construct(
			TxnConstructionRequest.create()
				.action(new FeeReservePut(tokenOwnerAcct, UInt256.FOUR))
				.action(nativeStake())
		)
			.signAndBuild(tokenOwnerKeyPair::sign);

		executeAndDecode(List.of(ActionType.STAKE), UInt256.FOUR, txn);
	}

	@Test
	public void unstakeIsParsedCorrectly() throws Exception {
		var txn1 = engine.construct(
			TxnConstructionRequest.create()
				.action(new FeeReservePut(tokenOwnerAcct, UInt256.FOUR))
				.action(nativeStake())
		).signAndBuild(tokenOwnerKeyPair::sign);
		engine.execute(List.of(txn1));
		var nextEpoch = engine.construct(new NextEpoch(0))
			.buildWithoutSignature();
		engine.execute(List.of(nextEpoch), null, PermissionLevel.SYSTEM);

		var txn2 = engine.construct(
			TxnConstructionRequest.create()
				.action(new FeeReservePut(tokenOwnerAcct, UInt256.FOUR))
				.action(nativeUnstake())
		).signAndBuild(tokenOwnerKeyPair::sign);

		executeAndDecode(List.of(ActionType.UNSTAKE), UInt256.FOUR, txn2);
	}

	@Test
	public void transferIsParsedCorrectly() throws Exception {
		//Use different token
		var txn = engine.construct(
			TxnConstructionRequest.create()
				.payFee(tokenOwnerAcct, UInt256.FOUR)
				.createMutableToken(tokDefII)
				.mint(tokenRriII, tokenOwnerAcct, Amount.ofTokens(10 * 2).toSubunits())
				.transfer(tokenRriII, tokenOwnerAcct, otherAccount, Amount.ofTokens(10).toSubunits())
		).signAndBuild(tokenOwnerKeyPair::sign);

		executeAndDecode(List.of(ActionType.UNKNOWN, ActionType.MINT, ActionType.TRANSFER), UInt256.FOUR, txn);
	}

	private void executeAndDecode(List<ActionType> expectedActions, UInt256 fee, Txn... txns) throws Exception {
		var list = engine.execute(List.of(txns), null, PermissionLevel.USER).getFirst();

		if (txns.length != 1) {
			return;
		}

		var timestamp = Instant.ofEpochMilli(Instant.now().toEpochMilli());

		list.stream()
			.map(txn -> {
				var actions = txn.getGroupedStateUpdates().stream()
					.map(REResourceAccounting::compute)
					.map(REResourceAccounting::bucketAccounting)
					.map(TwoActorEntry::parse)
					.collect(Collectors.toList());
				return parser.parse(txn, actions, timestamp, addr -> addr.toString(), (k, a) -> a);
			})
			.forEach(entry -> entry
				.onFailureDo(Assert::fail)
				.onSuccess(historyEntry -> assertEquals(fee, historyEntry.getFee()))
				.map(this::toActionTypes)
				.onSuccess(types -> assertEquals(expectedActions, types))
			);
	}

	private StakeTokens nativeStake() {
		return new StakeTokens(tokenOwnerAcct, validatorKeyPair.getPublicKey(), Amount.ofTokens(10).toSubunits());
	}

	private UnstakeTokens nativeUnstake() {
		return new UnstakeTokens(tokenOwnerAcct, validatorKeyPair.getPublicKey(), Amount.ofTokens(10).toSubunits());
	}

	private List<ActionType> toActionTypes(TxHistoryEntry txEntry) {
		return txEntry.getActions()
			.stream()
			.map(ActionEntry::getType)
			.collect(Collectors.toList());
	}
}
