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

import com.radixdlt.constraintmachine.exceptions.ProcedureException;

import java.util.function.Function;

public final class UpProcedure<S extends ReducerState, U extends Particle> implements Procedure {
	private final Class<S> reducerStateClass;
	private final Class<U> upClass;
	private final UpReducer<S, U> upReducer;
	private final Function<U, Authorization> authorization;

	public UpProcedure(
		Class<S> reducerStateClass,
		Class<U> upClass,
		Function<U, Authorization> authorization,
		UpReducer<S, U> upReducer
	) {
		this.reducerStateClass = reducerStateClass;
		this.upClass = upClass;
		this.upReducer = upReducer;
		this.authorization = authorization;
	}

	@Override
	public ProcedureKey key() {
		return ProcedureKey.of(reducerStateClass, OpSignature.ofSubstateUpdate(REOp.UP, upClass));
	}

	@Override
	public Authorization authorization(Object o) {
		return authorization.apply((U) o);
	}

	@Override
	public ReducerResult call(
		Object o,
		ReducerState reducerState,
		ReadableAddrs readableAddrs,
		ExecutionContext context
	) throws ProcedureException {
		return upReducer.reduce((S) reducerState, (U) o, context, readableAddrs);
	}
}
