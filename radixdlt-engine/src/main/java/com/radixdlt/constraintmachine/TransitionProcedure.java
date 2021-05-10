/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.constraintmachine;

import com.radixdlt.atomos.Result;
import com.radixdlt.store.ReadableAddrs;

/**
 * Application level "Bytecode" to be run per particle in the Constraint machine
 */
public interface TransitionProcedure<I extends Particle, O extends Particle, U extends ReducerState> {
	// TODO: move permission level to the "OS" level of paths rather than transitions

	Result precondition(SubstateWithArg<I> in, O outputParticle, U outputUsed, ReadableAddrs readableAddrs);

	InputOutputReducer<I, O, U> inputOutputReducer();

	default PermissionLevel requiredPermissionLevel(SubstateWithArg<I> in, O outputParticle, ReadableAddrs index) {
		return PermissionLevel.USER;
	}

	SignatureValidator<I, O> signatureValidator();
}
