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

package com.radixdlt.constraintmachine;

/**
 * Validates whether a specific transition procedure is permissible
 * @param <P> particle class
 */
public interface WitnessValidator<P extends Particle> {
	final class WitnessValidatorResult {
		private final String errorMessage;

		WitnessValidatorResult(String errorMessage) {
			this.errorMessage = errorMessage;
		}

		public static WitnessValidatorResult success() {
			return new WitnessValidatorResult(null);
		}

		public static WitnessValidatorResult error(String errorMessage) {
			return new WitnessValidatorResult(errorMessage);
		}

		public boolean isSuccess() {
			return this.errorMessage == null;
		}

		public boolean isError() {
			return this.errorMessage != null;
		}

		public String getErrorMessage() {
			return errorMessage;
		}
	}

	WitnessValidatorResult validate(P particle, WitnessData witnessData);
}
