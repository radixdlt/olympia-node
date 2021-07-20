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

package com.radixdlt.application.system.state;

import com.radixdlt.application.tokens.ResourceInBucket;
import com.radixdlt.application.tokens.Bucket;
import com.radixdlt.application.validators.state.ValidatorData;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.util.Objects;

import static com.radixdlt.application.validators.scrypt.ValidatorUpdateRakeConstraintScrypt.RAKE_MAX;

public final class ValidatorStakeData implements ResourceInBucket, ValidatorData {
	private final UInt256 totalStake;
	private final UInt256 totalOwnership;
	private final int rakePercentage;
	private final REAddr ownerAddr;
	private final boolean isRegistered;

	// Bucket keys
	private final ECPublicKey validatorKey;

	private ValidatorStakeData(
		ECPublicKey validatorKey,
		UInt256 totalStake,
		UInt256 totalOwnership,
		int rakePercentage,
		REAddr ownerAddr,
		boolean isRegistered
	) {
		if (totalStake.isZero() != totalOwnership.isZero()) {
			throw new IllegalArgumentException(
				"Zero must be equivalent between stake and ownership: " + totalStake + " " + totalOwnership
			);
		}
		this.validatorKey = Objects.requireNonNull(validatorKey);
		this.totalStake = totalStake;
		this.totalOwnership = totalOwnership;
		this.rakePercentage = rakePercentage;
		this.ownerAddr = ownerAddr;
		this.isRegistered = isRegistered;
	}

	public static ValidatorStakeData createVirtual(ECPublicKey validatorKey) {
		return new ValidatorStakeData(validatorKey, UInt256.ZERO, UInt256.ZERO, RAKE_MAX, REAddr.ofPubKeyAccount(validatorKey), false);
	}

	public static ValidatorStakeData create(
		ECPublicKey validatorKey,
		UInt256 totalStake,
		UInt256 totalOwnership,
		int rakePercentage,
		REAddr ownerAddress,
		boolean isRegistered
	) {
		return new ValidatorStakeData(
			validatorKey,
			totalStake,
			totalOwnership,
			rakePercentage,
			ownerAddress,
			isRegistered
		);
	}

	public UInt256 getTotalStake() {
		return totalStake;
	}

	public boolean isRegistered() {
		return isRegistered;
	}

	public REAddr getOwnerAddr() {
		return ownerAddr;
	}

	public int getRakePercentage() {
		return rakePercentage;
	}

	@Override
	public ECPublicKey getValidatorKey() {
		return validatorKey;
	}

	public UInt256 getTotalOwnership() {
		return this.totalOwnership;
	}

	@Override
	public String toString() {
		return String.format("%s{registered=%s stake=%s validator=%s ownership=%s rake=%s owner=%s}",
			getClass().getSimpleName(),
			isRegistered,
			totalStake,
			validatorKey.toHex().substring(0, 11),
			totalOwnership,
			rakePercentage,
			ownerAddr
		);
	}

	@Override
	public UInt256 getAmount() {
		return this.totalStake;
	}

	@Override
	public Bucket bucket() {
		return new StakeBucket(validatorKey);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ValidatorStakeData)) {
			return false;
		}
		var that = (ValidatorStakeData) o;
		return Objects.equals(validatorKey, that.validatorKey)
			&& Objects.equals(totalOwnership, that.totalOwnership)
			&& Objects.equals(totalStake, that.totalStake)
			&& Objects.equals(rakePercentage, that.rakePercentage)
			&& Objects.equals(ownerAddr, that.ownerAddr)
			&& this.isRegistered == that.isRegistered;
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			validatorKey,
			totalOwnership,
			totalStake,
			rakePercentage,
			ownerAddr,
			isRegistered
		);
	}
}
