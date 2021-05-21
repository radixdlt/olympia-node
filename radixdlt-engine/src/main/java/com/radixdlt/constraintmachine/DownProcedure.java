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

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.store.ReadableAddrs;
import com.radixdlt.utils.Pair;

import java.util.Optional;
import java.util.function.BiFunction;

public class DownProcedure<D extends Particle, S extends ReducerState> {
	private final Class<D> downClass;
	private final Class<S> reducerStateClass;
	private final DownReducer<D, S> downReducer;
	private final BiFunction<SubstateWithArg<D>, ReadableAddrs, PermissionLevel> permissionLevel;
	private final InputAuthorization<D> inputAuthorization;

	public DownProcedure(
		Class<D> downClass, Class<S> reducerStateClass,
		BiFunction<SubstateWithArg<D>, ReadableAddrs, PermissionLevel> permissionLevel,
		InputAuthorization<D> inputAuthorization,
		DownReducer<D, S> downReducer
	) {
		this.downClass = downClass;
		this.reducerStateClass = reducerStateClass;
		this.downReducer = downReducer;
		this.permissionLevel = permissionLevel;
		this.inputAuthorization = inputAuthorization;
	}

	public Pair<Class<? extends Particle>, Class<? extends ReducerState>> getDownProcedureKey() {
		return Pair.of(downClass, reducerStateClass);
	}

	public PermissionLevel permissionLevel(SubstateWithArg<D> downSubstate, ReadableAddrs readableAddrs) {
		return permissionLevel.apply(downSubstate, readableAddrs);
	}

	public boolean authorized(SubstateWithArg<D> downSubstate, ReadableAddrs readableAddrs, Optional<ECPublicKey> signedBy) {
		return inputAuthorization.verify(downSubstate, readableAddrs, signedBy);
	}

	public ReducerResult reduce(SubstateWithArg<D> downSubstate, S reducerState, ReadableAddrs readableAddrs) {
		return downReducer.reduce(downSubstate, reducerState, readableAddrs);
	}
}
