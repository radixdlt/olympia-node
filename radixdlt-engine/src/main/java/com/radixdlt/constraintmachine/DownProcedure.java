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

public class DownProcedure<D extends Particle, S extends ReducerState> implements MethodProcedure {
	private final Class<D> downClass;
	private final Class<S> reducerStateClass;
	private final DownReducer<D, S> downReducer;
	private final BiFunction<SubstateWithArg<D>, ReadableAddrs, PermissionLevel> permissionLevel;
	private final DownAuthorization<D> downAuthorization;

	public DownProcedure(
		Class<D> downClass, Class<S> reducerStateClass,
		BiFunction<SubstateWithArg<D>, ReadableAddrs, PermissionLevel> permissionLevel,
		DownAuthorization<D> downAuthorization,
		DownReducer<D, S> downReducer
	) {
		this.downClass = downClass;
		this.reducerStateClass = reducerStateClass;
		this.downReducer = downReducer;
		this.permissionLevel = permissionLevel;
		this.downAuthorization = downAuthorization;
	}

	public ProcedureKey getDownProcedureKey() {
		return ProcedureKey.of(downClass, reducerStateClass);
	}

	@Override
	public PermissionLevel permissionLevel(Object o, ReadableAddrs readableAddrs) {
		return permissionLevel.apply((SubstateWithArg<D>) o, readableAddrs);
	}

	@Override
	public void verifyAuthorization(Object o, ReadableAddrs readableAddrs, Optional<ECPublicKey> key) throws AuthorizationException {
		downAuthorization.verify((SubstateWithArg<D>) o, readableAddrs, key);
	}

	@Override
	public ReducerResult call(Object o, ReducerState reducerState, ReadableAddrs readableAddrs) throws ProcedureException {
		return downReducer.reduce((SubstateWithArg<D>) o, (S) reducerState, readableAddrs);
	}
}
