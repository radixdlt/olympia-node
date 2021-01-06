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

package com.radixdlt.atommodel.tokens;

import static com.radixdlt.atomos.Result.error;
import static com.radixdlt.atomos.Result.of;
import static com.radixdlt.atomos.Result.success;

import com.radixdlt.identifiers.RRI;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.WitnessData;
import java.util.Objects;

/**
 * Witness validation based on an RRI
 */
public enum TokenPermission {
	/**
	 * Only the token owner can do this
	 */
	TOKEN_OWNER_ONLY((tokDefRef, meta) -> of(
		meta.isSignedBy(tokDefRef.getAddress().getPublicKey()),
		() -> "must be signed by token owner: " + tokDefRef.getAddress())),

	/**
	 * Everyone can do this
	 */
	ALL((token, meta) -> success()),

	/**
	 * No-one can do this
	 */
	NONE((token, meta) -> error("no-one can do this"));

	private final TokenPermissionCheck check;

	TokenPermission(TokenPermissionCheck check) {
		this.check = Objects.requireNonNull(check);
	}

	/**
	 * Check whether this permissions allows an action of the definition in a specific atom
	 * @param tokDefRef the token reference
	 * @param meta the metadata of the containing atom
	 * @return the result of this check
	 */
	public Result check(RRI tokDefRef, WitnessData meta) {
		return check.check(tokDefRef, meta);
	}

	/**
	 * Internal interface for cleaner permission definition
	 */
	@FunctionalInterface
	private interface TokenPermissionCheck {
		Result check(RRI tokDefRef, WitnessData meta);
	}
}
