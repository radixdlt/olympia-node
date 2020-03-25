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

package com.radixdlt.consensus.validators;

import java.util.List;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import com.google.common.collect.ImmutableList;

/**
 * Result of validating a signature set.
 * <p>
 * TODO: Add methods for computing share of reward.
 */
@Immutable
public final class ValidationResult {
	private static final ValidationResult FAILURE = new ValidationResult(ImmutableList.of());

	private ImmutableList<Validator> validators;

	private ValidationResult(ImmutableList<Validator> validators) {
		this.validators = Objects.requireNonNull(validators);
	}

	/**
	 * Returns failure result.
	 *
	 * @return failure result.
	 */
	public static ValidationResult failure() {
		return FAILURE;
	}

	/**
	 * Returns a passed result, with the specified validators signing.
	 *
	 * @param signed The validators who have signed
	 * @return The validation result
	 */
	public static ValidationResult passed(ImmutableList<Validator> signed) {
		if (signed.isEmpty()) {
			throw new IllegalArgumentException("List of signing validators must not be empty");
		}
		return new ValidationResult(signed);
	}

	/**
	 * Returns true if this is a valid result, and sufficient
	 * validators signed to form a quorum.
	 *
	 * @return true if sufficient validators signed
	 */
	public boolean valid() {
		return !this.validators.isEmpty();
	}

	/**
	 * Returns the list of signing validators, which will be empty
	 * if insufficient validators signed.
	 *
	 * @return possibly empty list of signing validators
	 */
	public List<Validator> validators() {
		return this.validators;
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(this.validators);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof ValidationResult) {
			ValidationResult other = (ValidationResult) obj;
			return Objects.equals(this.validators, other.validators);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), this.validators);
	}
}
