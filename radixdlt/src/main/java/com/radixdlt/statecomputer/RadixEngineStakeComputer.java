/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.statecomputer;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.utils.UInt256;

/**
 * Track and compute staked amounts for specified validator keys.
 */
public interface RadixEngineStakeComputer {
	/**
	 * Add stake to specified delegate in the amount of the specified token.
	 *
	 * @param delegatedKey The public key the stake is being delegated to
	 * @param token The token of the staked amount
	 * @param amount The amount of the stake
	 * @return The next state of the stake computer
	 */
	RadixEngineStakeComputer addStake(ECPublicKey delegatedKey, RRI token, UInt256 amount);

	/**
	 * Remove stake from specified delegate in the amount of the specified token.
	 *
	 * @param delegatedKey The public key the delegated stake to remove
	 * @param token The token for the staked amount
	 * @param amount The amount of the stake being removed
	 * @return The next state of the stake computer
	 */
	RadixEngineStakeComputer removeStake(ECPublicKey delegatedKey, RRI token, UInt256 amount);

	/**
	 * Returns the staked amounts for the specified validators.
	 * Note that validators with zero stake must not be included in the output.
	 *
	 * @param validators The validators to retrieve staked amounts for
	 * @return The staked amounts, if any, for the specified validators
	 */
	ImmutableMap<ECPublicKey, UInt256> stakedAmounts(ImmutableSet<ECPublicKey> validators);
}