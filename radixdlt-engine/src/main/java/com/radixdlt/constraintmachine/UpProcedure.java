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

public final class UpProcedure<S extends ReducerState, U extends Particle> {
	private final Class<S> reducerStateClass;
	private final Class<U> upClass;
	private final UpReducer<S, U> upReducer;
	private final OutputAuthorization<U> outputAuthorization;
	private final BiFunction<U, ReadableAddrs, PermissionLevel> permissionLevel;

	public UpProcedure(
		Class<S> reducerStateClass,
		Class<U> upClass,
		BiFunction<U, ReadableAddrs, PermissionLevel> permissionLevel,
		OutputAuthorization<U> outputAuthorization,
		UpReducer<S, U> upReducer
	) {
		this.reducerStateClass = reducerStateClass;
		this.upClass = upClass;
		this.upReducer = upReducer;
		this.outputAuthorization = outputAuthorization;
		this.permissionLevel = permissionLevel;
	}

	public Pair<Class<? extends ReducerState>, Class<? extends Particle>> getUpProcedureKey() {
		return Pair.of(reducerStateClass, upClass);
	}

	public PermissionLevel permissionLevel(U upSubstate, ReadableAddrs readableAddrs) {
		return permissionLevel.apply(upSubstate, readableAddrs);
	}

	public boolean authorized(U upSubstate, ReadableAddrs readableAddrs, Optional<ECPublicKey> signedBy) {
		return outputAuthorization.verify(upSubstate, readableAddrs, signedBy);
	}

	public ReducerResult2 reduce(S reducerState, U upSubstate, ReadableAddrs readableAddrs) {
		return upReducer.reduce(reducerState, upSubstate, readableAddrs);
	}
}
