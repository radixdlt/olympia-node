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

public class VirtualUpProcedure<U extends Particle, S extends ReducerState> implements Procedure {
	private final Class<U> virtualizedClass;
	private final Class<S> reducerStateClass;
	private final VirtualUpReducer<U, S> virtualUpReducer;
	private final Supplier<Authorization> authorization;

	public VirtualUpProcedure(
		Class<S> reducerStateClass,
		Class<U> virtualizedClass,
		Supplier<Authorization> authorization,
		VirtualUpReducer<U, S> virtualUpReducer
	) {
		this.virtualizedClass = virtualizedClass;
		this.reducerStateClass = reducerStateClass;
		this.virtualUpReducer = virtualUpReducer;
		this.authorization = authorization;
	}

	@Override
	public ProcedureKey key() {
		return ProcedureKey.of(reducerStateClass, OpSignature.ofSubstateUpdate(REOp.VUP, virtualizedClass));
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
		return virtualUpReducer.reduce((S) reducerState, (Class<U>) o, context, immutableAddrs);
	}
}
