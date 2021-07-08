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

package com.radixdlt.application.system;

import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.constraintmachine.REEvent;

import java.util.List;

public class NextValidatorSetEvent implements REEvent {
	private final List<ValidatorStakeData> nextValidators;

	private NextValidatorSetEvent(List<ValidatorStakeData> nextValidators) {
		this.nextValidators = nextValidators;
	}

	public static NextValidatorSetEvent create(List<ValidatorStakeData> nextValidators) {
		return new NextValidatorSetEvent(List.copyOf(nextValidators));
	}

	public List<ValidatorStakeData> nextValidators() {
		return nextValidators;
	}
}
