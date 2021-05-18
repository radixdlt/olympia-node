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

import com.radixdlt.atommodel.system.Stake;
import com.radixdlt.engine.StateReducer;

import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class StakeReducer implements StateReducer<Stakes, Stake> {
	@Override
	public Class<Stakes> stateClass() {
		return Stakes.class;
	}

	@Override
	public Class<Stake> particleClass() {
		return Stake.class;
	}

	@Override
	public Supplier<Stakes> initial() {
		return Stakes::create;
	}

	@Override
	public BiFunction<Stakes, Stake, Stakes> outputReducer() {
		return (prev, p) -> prev.add(p.getValidatorKey(), p.getAmount());
	}

	@Override
	public BiFunction<Stakes, Stake, Stakes> inputReducer() {
		return (prev, p) -> prev.remove(p.getValidatorKey(), p.getAmount());
	}
}
