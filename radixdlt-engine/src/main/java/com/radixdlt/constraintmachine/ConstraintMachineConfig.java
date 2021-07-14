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

package com.radixdlt.constraintmachine;

import com.radixdlt.constraintmachine.meter.Meter;

public final class ConstraintMachineConfig {
	private final Procedures procedures;
	private final SubstateDeserialization deserialization;
	private final VirtualSubstateDeserialization virtualSubstateDeserialization;
	private final Meter metering;

	public ConstraintMachineConfig(
		Procedures procedures,
		SubstateDeserialization deserialization,
		VirtualSubstateDeserialization virtualSubstateDeserialization,
		Meter metering
	) {
		this.procedures = procedures;
		this.deserialization = deserialization;
		this.virtualSubstateDeserialization = virtualSubstateDeserialization;
		this.metering = metering;
	}

	public SubstateDeserialization getDeserialization() {
		return deserialization;
	}

	public VirtualSubstateDeserialization getVirtualSubstateDeserialization() {
		return virtualSubstateDeserialization;
	}

	public Procedures getProcedures() {
		return procedures;
	}

	public Meter getMeter() {
		return metering;
	}

}
