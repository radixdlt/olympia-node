/*
 * Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.api.archive.validators;

import com.google.common.base.Suppliers;
import com.google.inject.Inject;
import com.radixdlt.api.data.ValidatorInfoDetails;
import com.radixdlt.api.service.ValidatorInfoService;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;
import com.radixdlt.utils.functional.FunctionalUtils;
import com.radixdlt.utils.functional.Result;
import com.radixdlt.utils.functional.Result.Mapper2;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.radixdlt.utils.functional.FunctionalUtils.skipUntil;
import static com.radixdlt.utils.functional.Tuple.tuple;

public class ValidatorArchiveInfoService {
	private final RadixEngine<LedgerAndBFTProof> radixEngine;
	private final ValidatorInfoService validatorInfoService;
	private final BerkeleyValidatorUptimeArchiveStore uptimeStore;
	private final Supplier<List<ValidatorInfoDetails>> infoDetailsCache
		= Suppliers.memoizeWithExpiration(this::getAllValidators, 60, TimeUnit.SECONDS);

	@Inject
	public ValidatorArchiveInfoService(
		RadixEngine<LedgerAndBFTProof> radixEngine,
		BerkeleyValidatorUptimeArchiveStore uptimeStore,
		ValidatorInfoService validatorInfoService
	) {
		this.radixEngine = radixEngine;
		this.uptimeStore = uptimeStore;
		this.validatorInfoService = validatorInfoService;
	}

	public Mapper2<Optional<ECPublicKey>, List<ValidatorInfoDetails>> getValidators(int size, Optional<ECPublicKey> cursor) {
		var result = infoDetailsCache.get();
		var paged = cursor
			.map(key -> skipUntil(result, v -> v.getValidatorKey().equals(key)))
			.orElse(result);

		var list = paged.stream().limit(size).collect(Collectors.toList());
		var newCursor = list.stream().reduce(FunctionalUtils::findLast).map(ValidatorInfoDetails::getValidatorKey);

		return () -> Result.ok(tuple(newCursor, list));
	}

	public ValidatorInfoDetails getNextEpochValidator(ECPublicKey k) {
		var metadata = validatorInfoService.getMetadata(k);
		var curData = validatorInfoService.getValidatorStakeData(k);
		var owner = validatorInfoService.getNextEpochValidatorOwner(k).getOwner();
		var ownerEstimatedStake = validatorInfoService.getEstimatedIndividualStake(curData, owner);
		var preparedStakes = validatorInfoService.getPreparedStakes(k);
		var totalPreparedStakes = preparedStakes.values().stream().reduce(UInt384::add).orElse(UInt384.ZERO).getLow();
		var preparedUnstakes = validatorInfoService.getEstimatedPreparedUnstakes(curData);
		var totalPreparedUnstakes = preparedUnstakes.values().stream().reduce(UInt256::add).orElse(UInt256.ZERO);
		var totalStake = curData.getTotalStake().add(totalPreparedStakes).subtract(totalPreparedUnstakes);
		var ownerStake = ownerEstimatedStake
			.add(preparedStakes.getOrDefault(owner, UInt384.ZERO).getLow())
			.subtract(preparedUnstakes.getOrDefault(owner, UInt256.ZERO));
		var allowsDelegation = validatorInfoService.getAllowDelegationFlag(k).allowsDelegation();
		var isRegistered = validatorInfoService.getNextEpochRegisteredFlag(k).isRegistered();
		var percentage = validatorInfoService.getNextValidatorFee(k).getRakePercentage();
		var uptime = uptimeStore.getUptimeTwoWeeks(k);
		return ValidatorInfoDetails.create(
			k,
			owner,
			metadata.getName(),
			metadata.getUrl(),
			totalStake,
			ownerStake,
			allowsDelegation,
			isRegistered,
			percentage,
			uptime
		);
	}

	public List<ValidatorInfoDetails> getAllValidators() {
		var registered = radixEngine.reduce(ValidatorRegisteredCopy.class, new HashSet<ECPublicKey>(), (u, t) -> {
			if (t.isRegistered()) {
				u.add(t.getValidatorKey());
			}
			return u;
		});

		// TODO: Use NextEpoch action to compute all of this
		return registered.stream()
			.map(this::getNextEpochValidator)
			.sorted(Comparator.comparing(ValidatorInfoDetails::getTotalStake).reversed())
			.collect(Collectors.toList());
	}
}
