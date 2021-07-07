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

package com.radixdlt.application.system;

import com.radixdlt.accounting.REResourceAccounting;
import com.radixdlt.application.system.construction.FeeReserveCompleteConstructor;
import com.radixdlt.application.system.construction.FeeReservePutConstructor;
import com.radixdlt.application.system.scrypt.SystemConstraintScrypt;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.application.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.application.tokens.construction.MintTokenConstructor;
import com.radixdlt.application.tokens.construction.TransferTokensConstructorV2;
import com.radixdlt.application.tokens.scrypt.TokensConstraintScryptV3;
import com.radixdlt.application.tokens.state.AccountBucket;
import com.radixdlt.atom.REConstructor;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.FeeReserveComplete;
import com.radixdlt.atom.actions.FeeReservePut;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.exceptions.DefaultedSystemLoanException;
import com.radixdlt.constraintmachine.exceptions.ExecutionContextDestroyException;
import com.radixdlt.constraintmachine.meter.TxnSizeFeeMeter;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.utils.UInt256;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(Parameterized.class)
public class TxnSizeFeeTest {
	@Parameterized.Parameters
	public static Collection<Object[]> parameters() {
		return List.of(
			new Object[] {Amount.ofSubunits(UInt256.ONE)},
			new Object[] {Amount.ofSubunits(UInt256.TWO)},
			new Object[] {Amount.ofMicroTokens(2)}
		);
	}

	private RadixEngine<Void> engine;
	private EngineStore<Void> store;
	private final ECKeyPair key = ECKeyPair.generateNew();
	private final REAddr accountAddr = REAddr.ofPubKeyAccount(key.getPublicKey());
	private final Amount costPerByte;

	public TxnSizeFeeTest(Amount costPerByte) {
		this.costPerByte = costPerByte;
	}

	@Before
	public void setup() throws Exception {
		var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new TokensConstraintScryptV3());
		cmAtomOS.load(new SystemConstraintScrypt(Set.of()));
		var cm = new ConstraintMachine(
			cmAtomOS.getProcedures(),
			TxnSizeFeeMeter.create(costPerByte.toSubunits())
		);
		var parser = new REParser(cmAtomOS.buildSubstateDeserialization());
		var serialization = cmAtomOS.buildSubstateSerialization();
		this.store = new InMemoryEngineStore<>();
		this.engine = new RadixEngine<>(
			parser,
			serialization,
			REConstructor.newBuilder()
				.put(TransferToken.class, new TransferTokensConstructorV2())
				.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
				.put(MintToken.class, new MintTokenConstructor())
				.put(FeeReservePut.class, new FeeReservePutConstructor())
				.put(FeeReserveComplete.class, new FeeReserveCompleteConstructor(FeeTable.create(costPerByte, Amount.zero())))
				.build(),
			cm,
			store
		);
		var txn = this.engine.construct(
			TxnConstructionRequest.create()
				.action(new CreateMutableToken(null, "xrd", "xrd", "", "", ""))
				.action(new MintToken(REAddr.ofNativeToken(), accountAddr, Amount.ofTokens(2).toSubunits()))
		).buildWithoutSignature();
		this.engine.execute(List.of(txn), null, PermissionLevel.SYSTEM);
	}

	@Test
	public void paying_for_fees_should_work() throws Exception {
		var expectedTxnSize = 355;
		// Act
		var nextKey = ECKeyPair.generateNew();
		var to = REAddr.ofPubKeyAccount(nextKey.getPublicKey());
		var transfer = this.engine.construct(
			TxnConstructionRequest.create()
				.action(new FeeReservePut(accountAddr, costPerByte.toSubunits().multiply(UInt256.from(expectedTxnSize))))
				.action(new TransferToken(REAddr.ofNativeToken(), accountAddr, to, UInt256.FIVE)))
			.signAndBuild(key::sign);

		assertThat(transfer.getPayload().length).isEqualTo(expectedTxnSize);

		// Act
		var result = this.engine.execute(List.of(transfer));
	}

	@Test
	public void paying_for_fees_should_work_2() throws Exception {
		var expectedTxnSize = 355;
		// Act
		var nextKey = ECKeyPair.generateNew();
		var to = REAddr.ofPubKeyAccount(nextKey.getPublicKey());
		var transfer = this.engine.construct(
			TxnConstructionRequest.create()
				.action(new FeeReservePut(accountAddr, costPerByte.toSubunits().multiply(UInt256.from(expectedTxnSize))))
				.action(new TransferToken(REAddr.ofNativeToken(), accountAddr, to, UInt256.FIVE))
				.action(new FeeReserveComplete(accountAddr)))
			.signAndBuild(key::sign);

		assertThat(transfer.getPayload().length).isEqualTo(expectedTxnSize);

		// Act
		var result = this.engine.execute(List.of(transfer));
		REResourceAccounting.compute(result.get(0).getGroupedStateUpdates().get(0));
	}


	@Test
	public void paying_too_little_fees_should_fail() throws Exception {
		// Arrange
		var nextKey = ECKeyPair.generateNew();
		var to = REAddr.ofPubKeyAccount(nextKey.getPublicKey());
		var transfer = this.engine.construct(
			TxnConstructionRequest.create()
				.action(new FeeReservePut(accountAddr, UInt256.THREE))
				.action(new TransferToken(REAddr.ofNativeToken(), accountAddr, to, UInt256.FIVE)))
			.signAndBuild(key::sign);

		// Act
		assertThatThrownBy(() -> this.engine.execute(List.of(transfer)))
			.hasRootCauseInstanceOf(DefaultedSystemLoanException.class);
	}

	@Test
	public void paying_too_much_in_fees_should_fail() throws Exception {
		// Arrange
		var nextKey = ECKeyPair.generateNew();
		var to = REAddr.ofPubKeyAccount(nextKey.getPublicKey());
		var transfer = this.engine.construct(
			TxnConstructionRequest.create()
				.action(new FeeReservePut(accountAddr, Amount.ofTokens(1).toSubunits()))
				.action(new TransferToken(REAddr.ofNativeToken(), accountAddr, to, UInt256.TWO))
		).signAndBuild(key::sign);

		// Act
		assertThatThrownBy(() -> this.engine.execute(List.of(transfer)))
			.hasRootCauseInstanceOf(ExecutionContextDestroyException.class);
	}

	@Test
	public void put_then_take_reserve_should_work() throws Exception {
		// Arrange
		var nextKey = ECKeyPair.generateNew();
		var to = REAddr.ofPubKeyAccount(nextKey.getPublicKey());
		var transfer = this.engine.construct(
			TxnConstructionRequest.create()
				.action(new FeeReservePut(accountAddr, Amount.ofTokens(1).toSubunits()))
				.action(new TransferToken(REAddr.ofNativeToken(), accountAddr, to, UInt256.TWO))
				.action(new FeeReserveComplete(accountAddr)))
			.signAndBuild(key::sign);

		var expectedFee = costPerByte.toSubunits().multiply(UInt256.from(transfer.getPayload().length));
		var expectedRefund = new BigInteger(1, Amount.ofTokens(1).toSubunits().subtract(expectedFee).toByteArray());

		// Act
		var result = this.engine.execute(List.of(transfer));
		var refund = REResourceAccounting.compute(result.get(0).getGroupedStateUpdates().get(2))
			.bucketAccounting()
			.get(new AccountBucket(REAddr.ofNativeToken(), accountAddr));
		assertThat(refund).isEqualTo(expectedRefund);
	}
}
