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

package com.radixdlt.atomos;

import com.radixdlt.constraintmachine.EndProcedure;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;

public interface RoutineCalls {
	/**
	 * Defines a valid transition in the constraint machine as well as the
	 * requirements for executing that transition.
	 *
	 * @param <I> input particle type
	 * @param <U> output particle type
	 */
	<I extends Particle, O extends Particle, U extends ReducerState> void createTransition(
		TransitionToken<I, O, U> transitionToken,
		TransitionProcedure<I, O, U> transitionProcedure
	);

	<I extends Particle, S extends ReducerState> void createDownProcedure(DownProcedure<I, S> downProcedure);

	<O extends Particle, S extends ReducerState> void createUpProcedure(UpProcedure<S, O> upProcedure);

	<S extends ReducerState> void createEndProcedure(EndProcedure<S> endProcedure);
}
