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
import com.radixdlt.application.system.construction.FeeReservePutConstructor;
import com.radixdlt.application.system.construction.FeeReserveTakeConstructor;
import com.radixdlt.application.system.scrypt.SystemConstraintScrypt;
import com.radixdlt.application.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.application.tokens.construction.MintTokenConstructor;
import com.radixdlt.application.tokens.construction.TransferTokensConstructorV2;
import com.radixdlt.application.tokens.scrypt.TokensConstraintScryptV3;
import com.radixdlt.application.tokens.state.AccountBucket;
import com.radixdlt.atom.REConstructor;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.FeeReserveTake;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.FeeReservePut;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.exceptions.DefaultedSystemLoanException;
import com.radixdlt.constraintmachine.exceptions.ExecutionContextDestroyException;
import com.radixdlt.constraintmachine.meter.FixedFeeMeter;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.utils.UInt256;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FixedFeeTest {
	private RadixEngine<Void> engine;
	private EngineStore<Void> store;
	private final ECKeyPair key = ECKeyPair.generateNew();
	private final REAddr accountAddr = REAddr.ofPubKeyAccount(key.getPublicKey());

	@Before
	public void setup() throws Exception {
		var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new TokensConstraintScryptV3());
		cmAtomOS.load(new SystemConstraintScrypt(Set.of()));
		var cm = new ConstraintMachine(
			cmAtomOS.virtualizedUpParticles(),
			cmAtomOS.getProcedures(),
			FixedFeeMeter.create(UInt256.FIVE)
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
				.put(FeeReserveTake.class, new FeeReserveTakeConstructor())
				.build(),
			cm,
			store
		);
		var txn = this.engine.construct(
			TxnConstructionRequest.create()
				.action(new CreateMutableToken(null, "xrd", "xrd", "", "", ""))
				.action(new MintToken(REAddr.ofNativeToken(), accountAddr, UInt256.TEN))
		).buildWithoutSignature();
		this.engine.execute(List.of(txn), null, PermissionLevel.SYSTEM);
	}

	@Test
	public void paying_for_fees_should_work() throws Exception {
		// Act
		var nextKey = ECKeyPair.generateNew();
		var to = REAddr.ofPubKeyAccount(nextKey.getPublicKey());
		var transfer = this.engine.construct(
			TxnConstructionRequest.create()
				.action(new FeeReservePut(accountAddr, UInt256.FIVE))
				.action(new TransferToken(REAddr.ofNativeToken(), accountAddr, to, UInt256.FIVE)))
			.signAndBuild(key::sign);

		// Act
		var result = this.engine.execute(List.of(transfer));
		var accounting0 = REResourceAccounting.compute(result.get(0).getGroupedStateUpdates().get(0));
		assertThat(accounting0.bucketAccounting())
			.hasSize(1)
			.containsEntry(
				new AccountBucket(REAddr.ofNativeToken(), accountAddr),
				BigInteger.valueOf(-5)
			);
		var accounting1 = REResourceAccounting.compute(result.get(0).getGroupedStateUpdates().get(1));
		assertThat(accounting1.bucketAccounting())
			.hasSize(2)
			.containsEntry(
				new AccountBucket(REAddr.ofNativeToken(), accountAddr),
				BigInteger.valueOf(-5)
			)
			.containsEntry(
				new AccountBucket(REAddr.ofNativeToken(), to),
				BigInteger.valueOf(5)
			);
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
				.action(new FeeReservePut(accountAddr, UInt256.EIGHT))
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
				.action(new FeeReservePut(accountAddr, UInt256.EIGHT))
				.action(new TransferToken(REAddr.ofNativeToken(), accountAddr, to, UInt256.TWO))
				.action(new FeeReserveTake(accountAddr, UInt256.THREE)))
			.signAndBuild(key::sign);

		// Act
		var result = this.engine.execute(List.of(transfer));
		var accounting0 = REResourceAccounting.compute(result.get(0).getGroupedStateUpdates().get(0));
		assertThat(accounting0.bucketAccounting())
			.hasSize(1)
			.containsEntry(
				new AccountBucket(REAddr.ofNativeToken(), accountAddr),
				BigInteger.valueOf(-8)
			);
		var accounting1 = REResourceAccounting.compute(result.get(0).getGroupedStateUpdates().get(1));
		assertThat(accounting1.bucketAccounting())
			.hasSize(2)
			.containsEntry(
				new AccountBucket(REAddr.ofNativeToken(), accountAddr),
				BigInteger.valueOf(-2)
			)
			.containsEntry(
				new AccountBucket(REAddr.ofNativeToken(), to),
				BigInteger.valueOf(2)
			);
		var accounting2 = REResourceAccounting.compute(result.get(0).getGroupedStateUpdates().get(2));
		assertThat(accounting2.bucketAccounting())
			.hasSize(1)
			.containsEntry(
				new AccountBucket(REAddr.ofNativeToken(), accountAddr),
				BigInteger.valueOf(3)
			);
	}
}
