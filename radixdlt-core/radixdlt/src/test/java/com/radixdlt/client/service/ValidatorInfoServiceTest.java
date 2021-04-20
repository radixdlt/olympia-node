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

import org.junit.Assert;
import org.junit.Test;

import com.radixdlt.atommodel.validators.ValidatorParticle;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.identifiers.RadixAddress;
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
	private static final RadixAddress VALIDATOR_1 = RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
	private static final RadixAddress VALIDATOR_2 = RadixAddress.from("JEbhKQzBn4qJzWJFBbaPioA2GTeaQhuUjYWkanTE6N8VvvPpvM8");
	private static final RadixAddress VALIDATOR_3 = RadixAddress.from("23B6fH3FekJeP6e5guhZAk6n9z4fmTo5Tngo3a11Wg5R8gsWTV2x");

	@Test
	public void getValidatorsSuccessfullyReturnsFirstPage() {
		var validatorInfoService = setUpService();
		var result = validatorInfoService.getValidators(2, Optional.empty());

		result
			.onFailureDo(Assert::fail)
			.onSuccess(tuple -> tuple.map((cursor, list) -> {
				cursor.ifPresentOrElse(
					pos -> assertEquals(VALIDATOR_2, pos),
					() -> fail("Cursor must not be empty")
				);

				assertEquals(2, list.size());
				assertEquals(VALIDATOR_3.getPublicKey(), list.get(0).getValidatorKey());
				assertEquals(VALIDATOR_2.getPublicKey(), list.get(1).getValidatorKey());

				return null;
			}));
	}

	@Test
	public void getValidatorsSuccessfullyReturnsElementsUsingCursor() {
		var validatorInfoService = setUpService();
		var result
			= validatorInfoService.getValidators(1, Optional.of(VALIDATOR_2.getPublicKey()));

		result
			.onFailureDo(Assert::fail)
			.onSuccess(tuple -> tuple.map((cursor, list) -> {
				cursor.ifPresentOrElse(
					pos -> assertEquals(VALIDATOR_1, pos),
					() -> fail("Cursor must not be empty")
				);

				assertEquals(1, list.size());
				assertEquals(VALIDATOR_1.getPublicKey(), list.get(0).getValidatorKey());

				return null;
			}));
	}

	@Test
	public void answerIsEmptyIfCursorPointsToLastElement() {
		var validatorInfoService = setUpService();
		var result
			= validatorInfoService.getValidators(3, Optional.of(VALIDATOR_1.getPublicKey()));

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

		var particle1 = new ValidatorParticle(VALIDATOR_1.getPublicKey(), false, "V1", "http://v1.com");
		var particle2 = new ValidatorParticle(VALIDATOR_2.getPublicKey(), false, "V2", "http://v2.com");
		var particle3 = new ValidatorParticle(VALIDATOR_3.getPublicKey(), false, "V3", "http://v3.com");
		var validators = RegisteredValidators.create()
			.add(particle1)
			.add(particle2)
			.add(particle3);

		when(radixEngine.getComputedState(eq(RegisteredValidators.class))).thenReturn(validators);

		var stakes = Stakes.create()
			.add(VALIDATOR_1.getPublicKey(), UInt256.TEN)
			.add(VALIDATOR_2.getPublicKey(), UInt256.EIGHT)
			.add(VALIDATOR_3.getPublicKey(), UInt256.FIVE);

		when(radixEngine.getComputedState(eq(Stakes.class))).thenReturn(stakes);
		return validatorInfoService;
	}
}