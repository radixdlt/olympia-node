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

import com.google.common.collect.ImmutableSet;
import com.radixdlt.crypto.ECPublicKey;

/**
 * Radix engine state computer to keep track of registered validators.
 */
public interface RadixEngineValidatorsComputer {

	/**
	 * Add a validator to the list of registered validators.
	 *
	 * @param validatorKey The public key of the validator to be registered
	 * @return The next state of the validator computer
	 */
	RadixEngineValidatorsComputer addValidator(ECPublicKey validatorKey);

	/**
	 * Removes a validator to the list of registered validators.
	 *
	 * @param validatorKey The public key of the validator to be removed
	 * @return The next state of the validator computer
	 */
	RadixEngineValidatorsComputer removeValidator(ECPublicKey validatorKey);

	/**
	 * Returns a set of the currently active validators.
	 *
	 * @return The set of currently active validators
	 */
	ImmutableSet<ECPublicKey> activeValidators();
}