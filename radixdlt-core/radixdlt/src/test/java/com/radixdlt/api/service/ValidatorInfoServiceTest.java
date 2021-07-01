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

package com.radixdlt.api.service;

import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.statecomputer.forks.ForkManager;
import com.radixdlt.sync.CommittedReader;
import org.junit.Before;
import org.junit.Test;

import com.radixdlt.api.service.reducer.AllValidators;
import com.radixdlt.application.validators.state.ValidatorMetaData;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.constraintmachine.SubstateDeserialization;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.forks.RERules;
import com.radixdlt.store.EngineStore;
import com.radixdlt.systeminfo.InMemorySystemInfo;
import com.radixdlt.utils.UInt256;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ValidatorInfoServiceTest {
	private ECPublicKey validator1;
	private ECPublicKey validator2;
	private ECPublicKey validator3;

	@Before
	public void setup() throws PublicKeyException {
		// vb1qg32vr4eupdlm83hzyxmnhjuwxknfaqe0t2e68eqjqkvxff7qzm8yyhffdr
		validator1 = ECPublicKey.fromHex("0222a60eb9e05bfd9e37110db9de5c71ad34f4197ad59d1f20902cc3253e00b672");

		// vb1qfmpk5j982q53ysd2j0us42tss64m0svqm8dpu50j2knnltg67ghqkf0l5v
		validator2 = ECPublicKey.fromHex("02761b52453a8148920d549fc8554b84355dbe0c06ced0f28f92ad39fd68d79170");

		// vb1q2slgalhxmnffn4ynswk34qdgqglml05ep0lmzk7hvze4mjrzca9kakqjkc
		validator3 = ECPublicKey.fromHex("02a1f477f736e694cea49c1d68d40d4011fdfdf4c85ffd8adebb059aee43163a5b");
	}

	@Test
	public void getValidatorsSuccessfullyReturnsFirstPage() {
		var validatorInfoService = setUpService();
		var result = validatorInfoService.getValidators(2, Optional.empty());

		result.map((cursor, list) -> {
			cursor.ifPresentOrElse(
				pos -> assertEquals(validator2, pos),
				() -> fail("Cursor must not be empty")
			);

			assertEquals(2, list.size());
			assertEquals(validator3, list.get(0).getValidatorKey());
			assertEquals(validator2, list.get(1).getValidatorKey());

			return null;
		});
	}

	@Test
	public void getValidatorsSuccessfullyReturnsElementsUsingCursor() {
		var validatorInfoService = setUpService();
		var result
			= validatorInfoService.getValidators(1, Optional.of(validator2));

		result.map((cursor, list) -> {
			cursor.ifPresentOrElse(
				pos -> assertEquals(validator1, pos),
				() -> fail("Cursor must not be empty")
			);

			assertEquals(1, list.size());
			assertEquals(validator1, list.get(0).getValidatorKey());

			return null;
		});
	}

	@Test
	public void answerIsEmptyIfCursorPointsToLastElement() {
		var validatorInfoService = setUpService();
		var result
			= validatorInfoService.getValidators(3, Optional.of(validator1));

		result.map((cursor, list) -> {
			cursor.ifPresent(pos -> fail("Cursor must be empty"));
			assertEquals(0, list.size());
			return null;
		});
	}

	@SuppressWarnings("unchecked")
	private ValidatorInfoService setUpService() {
		var engineStore = (EngineStore<LedgerAndBFTProof>) mock(EngineStore.class);
		var committedReader = mock(CommittedReader.class);
		var inMemorySystemInfo = mock(InMemorySystemInfo.class);
		var forkManager = mock(ForkManager.class);
		var rules = mock(RERules.class);
		var parser = mock(REParser.class);

		var validatorInfoService = new ValidatorInfoService(
			engineStore, committedReader, forkManager, Addressing.ofNetwork(Network.LOCALNET)
		);

		var particle1 = new ValidatorMetaData(validator1, "V1", "http://v1.com", Optional.empty());
		var particle2 = new ValidatorMetaData(validator2, "V2", "http://v2.com", Optional.empty());
		var particle3 = new ValidatorMetaData(validator3, "V3", "http://v3.com", Optional.empty());
		var validators = AllValidators.create()
			.set(particle1)
			.set(particle2)
			.set(particle3)
			.setStake(validator1, UInt256.FIVE)
			.setStake(validator2, UInt256.EIGHT)
			.setStake(validator3, UInt256.TEN);

		when(inMemorySystemInfo.getCurrentProof()).thenReturn(LedgerProof.mock());
		final var forkConfig = mock(ForkConfig.class);
		when(forkConfig.getEngineRules()).thenReturn(rules);
		when(forkManager.getCurrentFork(any())).thenReturn(forkConfig);
		when(rules.getParser()).thenReturn(parser);
		when(parser.getSubstateDeserialization()).thenReturn(mock(SubstateDeserialization.class));
		when(engineStore.reduceUpParticles(any(), any(), any(), any())).thenReturn(validators);

		return validatorInfoService;
	}
}
