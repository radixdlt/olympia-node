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
package com.radixdlt.client.service;

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.radixdlt.atommodel.validators.ValidatorParticle;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RegisteredValidators;
import com.radixdlt.statecomputer.Stakes;
import com.radixdlt.utils.UInt256;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.eq;
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

		result
			.onFailureDo(Assert::fail)
			.onSuccess(tuple -> tuple.map((cursor, list) -> {
				cursor.ifPresentOrElse(
					pos -> assertEquals(validator2, pos),
					() -> fail("Cursor must not be empty")
				);

				assertEquals(2, list.size());
				assertEquals(validator3, list.get(0).getValidatorKey());
				assertEquals(validator2, list.get(1).getValidatorKey());

				return null;
			}));
	}

	@Test
	public void getValidatorsSuccessfullyReturnsElementsUsingCursor() {
		var validatorInfoService = setUpService();
		var result
			= validatorInfoService.getValidators(1, Optional.of(validator2));

		result
			.onFailureDo(Assert::fail)
			.onSuccess(tuple -> tuple.map((cursor, list) -> {
				cursor.ifPresentOrElse(
					pos -> assertEquals(validator1, pos),
					() -> fail("Cursor must not be empty")
				);

				assertEquals(1, list.size());
				assertEquals(validator1, list.get(0).getValidatorKey());

				return null;
			}));
	}

	@Test
	public void answerIsEmptyIfCursorPointsToLastElement() {
		var validatorInfoService = setUpService();
		var result
			= validatorInfoService.getValidators(3, Optional.of(validator1));

		result
			.onFailureDo(Assert::fail)
			.onSuccess(tuple -> tuple.map((cursor, list) -> {
				cursor.ifPresent(pos -> fail("Cursor must be empty"));
				assertEquals(0, list.size());
				return null;
			}));
	}

	@SuppressWarnings("unchecked")
	private ValidatorInfoService setUpService() {
		var radixEngine = (RadixEngine<LedgerAndBFTProof>) mock(RadixEngine.class);
		var validatorInfoService = new ValidatorInfoService(radixEngine, 0);

		var particle1 = new ValidatorParticle(validator1, false, "V1", "http://v1.com");
		var particle2 = new ValidatorParticle(validator2, false, "V2", "http://v2.com");
		var particle3 = new ValidatorParticle(validator3, false, "V3", "http://v3.com");
		var validators = RegisteredValidators.create()
			.add(particle1)
			.add(particle2)
			.add(particle3);

		when(radixEngine.getComputedState(eq(RegisteredValidators.class))).thenReturn(validators);

		var stakes = Stakes.create()
			.add(validator1, UInt256.TEN)
			.add(validator2, UInt256.EIGHT)
			.add(validator3, UInt256.FIVE);

		when(radixEngine.getComputedState(eq(Stakes.class))).thenReturn(stakes);
		return validatorInfoService;
	}
}