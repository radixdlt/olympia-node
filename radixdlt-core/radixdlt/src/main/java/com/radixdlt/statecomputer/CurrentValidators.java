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

package com.radixdlt.statecomputer;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.system.state.ValidatorEpochData;
import com.radixdlt.crypto.ECPublicKey;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class CurrentValidators {
	private final Map<ECPublicKey, Long> epochData;

	private CurrentValidators(Map<ECPublicKey, Long> epochData) {
		this.epochData = epochData;
	}

	public static CurrentValidators create() {
		return new CurrentValidators(Map.of());
	}

	public Set<ECPublicKey> validatorKeys() {
		return epochData.keySet();
	}

	public CurrentValidators add(ValidatorEpochData validatorEpochData) {
		var map = ImmutableMap.<ECPublicKey, Long>builder()
			.putAll(epochData)
			.put(validatorEpochData.validatorKey(), validatorEpochData.proposalsCompleted())
			.build();

		return new CurrentValidators(map);
	}

	public CurrentValidators remove(ValidatorEpochData validatorEpochData) {
		var map = epochData.entrySet().stream()
			.filter(v -> !v.getKey().equals(validatorEpochData.validatorKey()))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		return new CurrentValidators(map);
	}
}
