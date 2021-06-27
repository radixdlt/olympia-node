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

package com.radixdlt.constraintmachine.meter;

import com.radixdlt.constraintmachine.ExecutionContext;
import com.radixdlt.constraintmachine.ProcedureKey;
import com.radixdlt.constraintmachine.exceptions.AuthorizationException;

public final class Meters {
	private Meters() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	public static Meter combine(Meter m0, Meter m1) {
		return new Meter() {
			@Override
			public void onUserProcedure(ProcedureKey procedureKey, Object param, ExecutionContext context) throws AuthorizationException {
				m0.onUserProcedure(procedureKey, param, context);
				m1.onUserProcedure(procedureKey, param, context);
			}

			@Override
			public void onSuperUserProcedure(ProcedureKey procedureKey, Object param, ExecutionContext context) throws AuthorizationException {
				m0.onSuperUserProcedure(procedureKey, param, context);
				m1.onSuperUserProcedure(procedureKey, param, context);
			}

			@Override
			public void onSigInstruction(ExecutionContext context) throws AuthorizationException {
				m0.onSigInstruction(context);
				m1.onSigInstruction(context);
			}
		};
	}
}
