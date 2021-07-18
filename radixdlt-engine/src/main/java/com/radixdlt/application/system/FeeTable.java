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

import com.radixdlt.application.tokens.Amount;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.utils.UInt256;

import java.util.HashMap;
import java.util.Map;

public final class FeeTable {
	private final Amount perByteFee;
	private final Map<Class<? extends Particle>, UInt256> perUpSubstateFee;

	private FeeTable(Amount perByteFee, Map<Class<? extends Particle>, UInt256> perUpSubstateFee) {
		this.perByteFee = perByteFee;
		this.perUpSubstateFee = perUpSubstateFee;
	}

	public static FeeTable create(Amount perByteFee, Map<Class<? extends Particle>, Amount> perUpSubstateFee) {
		var map = new HashMap<Class<? extends Particle>, UInt256>();
		perUpSubstateFee.forEach((k, v) -> map.put(k, v.toSubunits()));
		return new FeeTable(perByteFee, map);
	}

	public static FeeTable noFees() {
		return new FeeTable(Amount.zero(), Map.of());
	}

	public UInt256 getPerByteFee() {
		return perByteFee.toSubunits();
	}

	public Map<Class<? extends Particle>, UInt256> getPerUpSubstateFee() {
		return perUpSubstateFee;
	}
}
