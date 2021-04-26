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

import com.google.inject.Inject;
import com.radixdlt.client.ValidatorAddress;
import com.radixdlt.client.api.ValidatorInfoDetails;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RegisteredValidators;
import com.radixdlt.statecomputer.Stakes;
import com.radixdlt.statecomputer.ValidatorDetails;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;
import com.radixdlt.utils.functional.FunctionalUtils;
import com.radixdlt.utils.functional.Tuple.Tuple2;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.radixdlt.utils.functional.Tuple.tuple;

public class ValidatorInfoService {
	private final RadixEngine<LedgerAndBFTProof> radixEngine;

	@Inject
	public ValidatorInfoService(RadixEngine<LedgerAndBFTProof> radixEngine) {
		this.radixEngine = radixEngine;
	}

	public Result<Tuple2<Optional<ECPublicKey>, List<ValidatorInfoDetails>>> getValidators(
		int size, Optional<ECPublicKey> cursor
	) {
		var validators = radixEngine.getComputedState(RegisteredValidators.class);
		var stakes = radixEngine.getComputedState(Stakes.class);

		var result = validators.map((address, details) -> fillDetails(address, details, stakes));
		result.sort(Comparator.comparing(ValidatorInfoDetails::getValidatorAddress));

		var paged = cursor
			.map(key -> FunctionalUtils.skipUntil(result, v -> v.getValidatorKey().equals(key)))
			.orElse(result);

		var list = paged.stream().limit(size).collect(Collectors.toList());
		var newCursor = list.stream().reduce(FunctionalUtils::findLast).map(ValidatorInfoDetails::getValidatorKey);

		return Result.ok(tuple(newCursor, list));
	}

	public Result<ValidatorInfoDetails> getValidator(ECPublicKey validatorPublicKey) {
		var validators = radixEngine.getComputedState(RegisteredValidators.class);
		var stakes = radixEngine.getComputedState(Stakes.class);

		return Result.fromOptional(
			validators.mapSingle(validatorPublicKey, details -> fillDetails(validatorPublicKey, details, stakes)),
			"Validator {0} not found",
			ValidatorAddress.of(validatorPublicKey)
		);
	}

	private ValidatorInfoDetails fillDetails(ECPublicKey validatorKey, ValidatorDetails details, Stakes stakes) {
		return ValidatorInfoDetails.create(
			validatorKey,
			REAddr.ofPubKeyAccount(validatorKey),
			details.getName(),
			details.getUrl(),
			stakes.getStake(validatorKey).orElse(UInt256.ZERO),
			UInt256.ZERO,
			true
		);
	}
}
