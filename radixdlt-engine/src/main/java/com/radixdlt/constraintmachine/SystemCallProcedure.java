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
import com.radixdlt.identifiers.REAddr;

import java.util.function.Supplier;

public class SystemCallProcedure<S extends ReducerState> implements Procedure {
	private final Class<S> reducerStateClass;
	private final REAddr addr;
	private final SystemCallReducer<S> reducer;
	private final Supplier<Authorization> authorization;

	public SystemCallProcedure(
		Class<S> reducerStateClass,
		REAddr addr,
		Supplier<Authorization> authorization,
		SystemCallReducer<S> reducer
	) {
		this.reducerStateClass = reducerStateClass;
		this.addr = addr;
		this.reducer = reducer;
		this.authorization = authorization;
	}

	@Override
	public ProcedureKey key() {
		return ProcedureKey.of(reducerStateClass, OpSignature.ofMethod(REOp.SYSCALL, addr));
	}

	@Override
	public Authorization authorization(Object o) {
		return authorization.get();
	}

	@Override
	public ReducerResult call(
		Object o,
		ReducerState reducerState,
		Resources immutableAddrs,
		ExecutionContext context
	) throws ProcedureException {
		try {
			return reducer.reduce((S) reducerState, (CallData) o, context);
		} catch (Exception e) {
			throw new ProcedureException(e);
		}
	}
}
