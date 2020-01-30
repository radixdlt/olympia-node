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
 * The state of a {@link Particle}
 */
public enum Spin {
	NEUTRAL(0),
	UP(1),
	DOWN(-1);

	private final int intValue;

	Spin(int intValue) {
		this.intValue = intValue;
	}

	public static Spin valueOf(int intValue) {
		switch (intValue) {
			case 1: return UP;
			case 0: return NEUTRAL;
			case -1: return DOWN;
			default: throw new IllegalArgumentException("No spin type of value: " + intValue);
		}
	}

	public int intValue() {
		return intValue;
	}

	@Override
	public String toString() {
		return this.name();
	}
}