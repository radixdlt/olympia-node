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

public final class ReadProcedure<D extends Particle, S extends ReducerState> implements Procedure  {
	private final Class<D> readClass;
	private final Class<S> reducerStateClass;
	private final ReadReducer<D, S> readReducer;
	private final Function<D, Authorization> authorization;

	public ReadProcedure(
		Class<S> reducerStateClass, Class<D> readClass,
		Function<D, Authorization> authorization,
		ReadReducer<D, S> readReducer
	) {
		this.readClass = readClass;
		this.reducerStateClass = reducerStateClass;
		this.readReducer = readReducer;
		this.authorization = authorization;
	}

	@Override
	public ProcedureKey key() {
		return ProcedureKey.of(reducerStateClass, OpSignature.ofSubstateUpdate(REOp.READ, readClass));
	}

	@Override
	public Authorization authorization(Object o) {
		return authorization.apply((D) o);
	}

	@Override
	public ReducerResult call(
		Object o,
		ReducerState reducerState,
		ImmutableAddrs immutableAddrs,
		ExecutionContext context
	) throws ProcedureException {
		return readReducer.reduce((S) reducerState, (D) o, immutableAddrs);
	}
}
