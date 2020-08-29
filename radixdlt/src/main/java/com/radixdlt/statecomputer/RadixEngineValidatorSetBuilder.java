/*
 * (C) Copyright 2020 Radix DLT Ltd
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
 */

package com.radixdlt.statecomputer;

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helper class to build validator sets from radix engine state
 */
public final class RadixEngineValidatorSetBuilder {
	private final HashSet<ECPublicKey> validators;
	private final LinkedList<ECPublicKey> lastRemovedValidators = new LinkedList<>();

	RadixEngineValidatorSetBuilder(BFTValidatorSet validatorSet) {
		this.validators = validatorSet.getValidators().stream().map(v -> v.getNode().getKey()).collect(Collectors.toCollection(HashSet::new));
	}

	RadixEngineValidatorSetBuilder removeValidator(RadixAddress validatorAddress) {
		this.validators.remove(validatorAddress.getPublicKey());
		if (lastRemovedValidators.size() == 2) {
			lastRemovedValidators.removeFirst();
		}
		lastRemovedValidators.addLast(validatorAddress.getPublicKey());
		return this;
	}

	RadixEngineValidatorSetBuilder addValidator(RadixAddress validatorAddress) {
		this.validators.add(validatorAddress.getPublicKey());
		return this;
	}

	BFTValidatorSet build() {
		Stream<ECPublicKey> validatorAddresses = validators.size() >= 2
			? validators.stream()
			: lastRemovedValidators.stream();

		return BFTValidatorSet.from(
			validatorAddresses
				.map(BFTNode::create)
				.map(node -> BFTValidator.from(node, UInt256.ONE))
		);
	}
}
