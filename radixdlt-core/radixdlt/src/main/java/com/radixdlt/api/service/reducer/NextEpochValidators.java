/* Copyright 2021 Radix DLT Ltd incorporated in England.
 * 
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 * 
 * radixfoundation.org/licenses/LICENSE-v1
 * 
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

package com.radixdlt.api.service.reducer;

import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.tokens.state.PreparedStake;
import com.radixdlt.application.tokens.state.PreparedUnstakeOwnership;
import com.radixdlt.application.validators.state.AllowDelegationFlag;
import com.radixdlt.application.validators.state.ValidatorMetaData;
import com.radixdlt.application.validators.state.ValidatorOwnerCopy;
import com.radixdlt.application.validators.state.ValidatorRakeCopy;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.statecomputer.ValidatorDetails;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.radixdlt.utils.functional.FunctionalUtils.mergeAll;

import static java.util.Optional.ofNullable;

/**
 * Wrapper class for registered validators
 */
public final class NextEpochValidators {
	private final Map<ECPublicKey, ValidatorMetaData> metadataMap;
	private final Map<ECPublicKey, UInt256> stakeMap;
	private final Map<ECPublicKey, UInt256> stakeOwnershipMap;
	private final Map<ECPublicKey, REAddr> ownersMap;
	private final Map<ECPublicKey, Boolean> delegationFlagsMap;
	private final Map<ECPublicKey, Integer> feesMap;
	private final Set<ECPublicKey> registered;

	private NextEpochValidators() {
		this.registered = new HashSet<>();
		this.stakeMap = new HashMap<>();
		this.stakeOwnershipMap = new HashMap<>();
		this.ownersMap = new HashMap<>();
		this.delegationFlagsMap = new HashMap<>();
		this.metadataMap = new HashMap<>();
		this.feesMap = new HashMap<>();
	}

	public static NextEpochValidators create() {
		return new NextEpochValidators();
	}

	public void process(Particle p) {
		if (p instanceof ValidatorRegisteredCopy) {
			var v = (ValidatorRegisteredCopy) p;
			if (!v.isRegistered()) {
				registered.remove(v.getValidatorKey());
			} else {
				registered.add(v.getValidatorKey());
			}
		} else if (p instanceof ValidatorMetaData) {
			var s = (ValidatorMetaData) p;
			metadataMap.put(s.getValidatorKey(), s);
		} else if (p instanceof PreparedStake) {
			var s = (PreparedStake) p;
			stakeMap.merge(s.getDelegateKey(), s.getAmount(), UInt256::add);
		} else if (p instanceof PreparedUnstakeOwnership) {
			var s = (PreparedUnstakeOwnership) p;
			var totalOwnership = stakeOwnershipMap.get(s.getDelegateKey());
			var totalStake = UInt384.from(stakeMap.get(s.getDelegateKey()));
			var stakeRemoved = totalStake.multiply(s.getAmount()).divide(totalOwnership);
			stakeMap.put(s.getDelegateKey(), totalStake.subtract(stakeRemoved).getLow());
			stakeOwnershipMap.put(s.getDelegateKey(), totalOwnership.subtract(s.getAmount()));
		} else if (p instanceof ValidatorOwnerCopy) {
			var s = (ValidatorOwnerCopy) p;
			ownersMap.put(s.getValidatorKey(), s.getOwner());
		} else if (p instanceof AllowDelegationFlag) {
			var s = (AllowDelegationFlag) p;
			delegationFlagsMap.put(s.getValidatorKey(), s.allowsDelegation());
		} else if (p instanceof ValidatorRakeCopy) {
			var s = (ValidatorRakeCopy) p;
			feesMap.put(s.getValidatorKey(), s.getRakePercentage());
		} else {
			var s = (ValidatorStakeData) p;
			ownersMap.put(s.getValidatorKey(), s.getOwnerAddr());
			stakeOwnershipMap.put(s.getValidatorKey(), s.getTotalOwnership());
			stakeMap.put(s.getValidatorKey(), s.getAmount());
			if (!s.isRegistered()) {
				registered.remove(s.getValidatorKey());
			} else {
				registered.add(s.getValidatorKey());
			}
		}
	}

	public ValidatorMetaData getMetadata(ECPublicKey validatorKey) {
		return ofNullable(metadataMap.get(validatorKey)).orElse(ValidatorMetaData.createVirtual(validatorKey));
	}

	public REAddr getOwner(ECPublicKey validatorKey) {
		return ofNullable(ownersMap.get(validatorKey))
			.orElse(REAddr.ofPubKeyAccount(validatorKey));
	}

	public UInt256 getStake(ECPublicKey validatorKey) {
		return ofNullable(stakeMap.get(validatorKey))
			.orElse(UInt256.ZERO);
	}

	public Boolean allowsDelegation(ECPublicKey validatorKey) {
		return ofNullable(delegationFlagsMap.get(validatorKey))
			.orElse(Boolean.FALSE);
	}

	public UInt256 getOwnerStake(ECPublicKey key) {
		return getOwner(key)
			.publicKey()
			.map(this::getStake)
			.orElse(UInt256.ZERO);
	}

	private int getRake(ECPublicKey validatorKey) {
		return ofNullable(feesMap.get(validatorKey)).orElse(0);
	}

	public <T> List<T> map(Function<ValidatorDetails, T> mapper) {
		return mergeAll(
			registered,
			metadataMap.keySet(),
			stakeMap.keySet(),
			ownersMap.keySet(),
			delegationFlagsMap.keySet(),
			feesMap.keySet()
		).stream()
			.map(this::fillDetails)
			.map(mapper)
			.collect(Collectors.toList());
	}

	public long count() {
		return registered.size();
	}

	private ValidatorDetails fillDetails(ECPublicKey validatorKey) {
		return ValidatorDetails.fromParticle(
			getMetadata(validatorKey),
			getOwner(validatorKey),
			getStake(validatorKey),
			getOwnerStake(validatorKey),
			allowsDelegation(validatorKey),
			registered.contains(validatorKey),
			getRake(validatorKey)
		);
	}
}
