/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.application.faucet;

import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.RadixAddress;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Faucet request object
 */
public final class FaucetRequest {
	private final RadixAddress address;
	private final Consumer<AID> onSuccess;
	private final Consumer<String> onError;

	private FaucetRequest(
		RadixAddress address,
		Consumer<AID> onSuccess,
		Consumer<String> onError
	) {
		this.address = address;
		this.onSuccess = onSuccess;
		this.onError = onError;
	}

	public static FaucetRequest create(
		RadixAddress address,
		Consumer<AID> onSuccess,
		Consumer<String> onFailure
	) {
		Objects.requireNonNull(address);
		Objects.requireNonNull(onSuccess);
		Objects.requireNonNull(onFailure);
		return new FaucetRequest(address, onSuccess, onFailure);
	}

	public RadixAddress getAddress() {
		return address;
	}

	public void onSuccess(AID aid) {
		onSuccess.accept(aid);
	}

	public void onFailure(String errorMessage) {
		onError.accept(errorMessage);
	}

	@Override
	public String toString() {
		return String.format("%s{address=%s}", this.getClass().getSimpleName(), address);
	}
}