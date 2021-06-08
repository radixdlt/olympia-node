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

import java.util.Optional;
import java.util.function.BiFunction;

public final class UpProcedure<S extends ReducerState, U extends Particle> implements MethodProcedure {
	private final Class<S> reducerStateClass;
	private final Class<U> upClass;
	private final UpReducer<S, U> upReducer;
	private final UpAuthorization<U> upAuthorization;
	private final BiFunction<U, ReadableAddrs, PermissionLevel> permissionLevel;

	public UpProcedure(
		Class<S> reducerStateClass,
		Class<U> upClass,
		BiFunction<U, ReadableAddrs, PermissionLevel> permissionLevel,
		UpAuthorization<U> upAuthorization,
		UpReducer<S, U> upReducer
	) {
		this.reducerStateClass = reducerStateClass;
		this.upClass = upClass;
		this.upReducer = upReducer;
		this.upAuthorization = upAuthorization;
		this.permissionLevel = permissionLevel;
	}

	public ProcedureKey getUpProcedureKey() {
		return ProcedureKey.of(upClass, reducerStateClass);
	}

	@Override
	public PermissionLevel permissionLevel(Object o, ReadableAddrs readableAddrs) {
		return permissionLevel.apply((U) o, readableAddrs);
	}

	@Override
	public void verifyAuthorization(Object o, ReadableAddrs readableAddrs, Optional<ECPublicKey> key) throws AuthorizationException {
		upAuthorization.verify((U) o, readableAddrs, key);
	}

	@Override
	public ReducerResult call(Object o, ReducerState reducerState, ReadableAddrs readableAddrs) throws ProcedureException {
		return upReducer.reduce((S) reducerState, (U) o, readableAddrs);
	}
}
