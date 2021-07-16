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
		return ofNullable(metadataMap.get(validatorKey)).orElse(new ValidatorMetaData(validatorKey));
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
