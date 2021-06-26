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

public class EndProcedure<S extends ReducerState> implements Procedure {
	private final Class<S> reducerStateClass;
	private final Function<S, Authorization> authorization;
	private final EndReducer<S> endReducer;

	public EndProcedure(
		Class<S> reducerStateClass,
		Function<S, Authorization> authorization,
		EndReducer<S> endReducer
	) {
		this.reducerStateClass = reducerStateClass;
		this.authorization = authorization;
		this.endReducer = endReducer;
	}

	@Override
	public ProcedureKey key() {
		return ProcedureKey.of(reducerStateClass, OpSignature.ofSubstateUpdate(REOp.END, null));
	}

	@Override
	public Authorization authorization(Object o) {
		return authorization.apply((S) o);
	}

	@Override
	public ReducerResult call(
		Object o,
		ReducerState reducerState,
		ImmutableAddrs immutableAddrs,
		ExecutionContext executionContext
	) throws ProcedureException {
		endReducer.reduce((S) reducerState, executionContext, immutableAddrs);
		return ReducerResult.complete();
	}
}
