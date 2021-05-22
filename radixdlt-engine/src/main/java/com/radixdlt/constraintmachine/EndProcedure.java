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

public class EndProcedure<S extends ReducerState> implements MethodProcedure {
	private final Class<S> reducerStateClass;
	private final BiFunction<S, ReadableAddrs, PermissionLevel> permissionLevel;
	private final EndAuthorization<S> endAuthorization;
	private final EndReducer<S> endReducer;

	public EndProcedure(
		Class<S> reducerStateClass,
		BiFunction<S, ReadableAddrs, PermissionLevel> permissionLevel,
		EndAuthorization<S> endAuthorization,
		EndReducer<S> endReducer
	) {
		this.reducerStateClass = reducerStateClass;
		this.permissionLevel = permissionLevel;
		this.endAuthorization = endAuthorization;
		this.endReducer = endReducer;
	}

	public Class<? extends ReducerState> getEndProcedureKey() {
		return reducerStateClass;
	}

	@Override
	public PermissionLevel permissionLevel(Object o, ReadableAddrs readableAddrs) {
		return permissionLevel.apply((S) o, readableAddrs);
	}

	@Override
	public void verifyAuthorization(Object o, ReadableAddrs readableAddrs, Optional<ECPublicKey> key) throws AuthorizationException {
		endAuthorization.verify((S) o, readableAddrs, key);
	}

	@Override
	public ReducerResult call(Object o, ReducerState reducerState, ReadableAddrs readableAddrs) throws ProcedureException {
		return ReducerResult.complete(endReducer.reduce((S) reducerState, readableAddrs));
	}
}
