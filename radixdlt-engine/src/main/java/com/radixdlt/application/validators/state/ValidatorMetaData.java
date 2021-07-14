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

package com.radixdlt.application.validators.state;

import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.crypto.ECPublicKey;

import java.util.Objects;

public final class ValidatorMetaData implements ValidatorData {
	private final ECPublicKey validatorKey;
	private final String name;
	private final String url;

	public ValidatorMetaData(ECPublicKey validatorKey) {
		this(validatorKey, "", "");
	}

	public ValidatorMetaData(
		ECPublicKey validatorKey,
		String name,
		String url
	) {
		this.validatorKey = Objects.requireNonNull(validatorKey);
		this.name = Objects.requireNonNull(name);
		this.url = REFieldSerialization.requireValidUrl(url);
	}

	@Override
	public ECPublicKey getValidatorKey() {
		return validatorKey;
	}

	public String getName() {
		return name;
	}

	public String getUrl() {
		return url;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.validatorKey, this.name, this.url);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof ValidatorMetaData)) {
			return false;
		}
		final var that = (ValidatorMetaData) obj;
		return Objects.equals(this.validatorKey, that.validatorKey)
			&& Objects.equals(this.name, that.name)
			&& Objects.equals(this.url, that.url);
	}

	@Override
	public String toString() {
		return String.format(
			"%s[%s, %s]",
			getClass().getSimpleName(), getValidatorKey(), getUrl()
		);
	}
}
