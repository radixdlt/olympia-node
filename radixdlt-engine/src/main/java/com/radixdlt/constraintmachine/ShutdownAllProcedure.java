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

import java.util.function.Supplier;

public class ShutdownAllProcedure<D extends Particle, S extends ReducerState> implements Procedure {
	private final Class<D> downClass;
	private final Class<S> reducerStateClass;
	private final ShutdownAllReducer<D, S> downReducer;
	private final Supplier<Authorization> authorization;

	public ShutdownAllProcedure(
		Class<D> downClass,
		Class<S> reducerStateClass,
		Supplier<Authorization> authorization,
		ShutdownAllReducer<D, S> downReducer
	) {
		this.downClass = downClass;
		this.reducerStateClass = reducerStateClass;
		this.downReducer = downReducer;
		this.authorization = authorization;
	}

	@Override
	public ProcedureKey key() {
		return ProcedureKey.of(reducerStateClass, OpSignature.ofSubstateUpdate(REOp.DOWNALL, downClass));
	}

	@Override
	public Authorization authorization(Object o) {
		return authorization.get();
	}

	@Override
	public ReducerResult call(
		Object o,
		ReducerState reducerState,
		ImmutableAddrs immutableAddrs,
		ExecutionContext context
	) throws ProcedureException {
		return downReducer.reduce((ShutdownAll<D>) o, (S) reducerState, immutableAddrs);
	}
}
