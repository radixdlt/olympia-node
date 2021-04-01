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

package com.radixdlt.application.validator;

import com.radixdlt.identifiers.AID;

import java.util.function.Consumer;

/**
 * Event signaling whether a node should be registered/unregistered
 * as a validator
 */
public final class ValidatorRegistration {
	private final boolean enabled;
	private final Consumer<AID> onSuccess;
	private final Consumer<String> onError;

	private ValidatorRegistration(
		boolean enabled,
		Consumer<AID> onSuccess,
		Consumer<String> onError
	) {
		this.enabled = enabled;
		this.onSuccess = onSuccess;
		this.onError = onError;
	}

	public static ValidatorRegistration create(boolean register) {
		return new ValidatorRegistration(register, aid -> { }, err -> { });
	}

	public static ValidatorRegistration create(
		boolean register,
		Consumer<AID> onSuccess,
		Consumer<String> onError
	) {
		return new ValidatorRegistration(register, onSuccess, onError);
	}

	public boolean isRegister() {
		return enabled;
	}

	public void onSuccess(AID aid) {
		onSuccess.accept(aid);
	}

	public void onFailure(String errorMessage) {
		onError.accept(errorMessage);
	}

	@Override
	public String toString() {
		return String.format("%s{%s}", this.getClass().getSimpleName(), this.enabled);
	}
}
