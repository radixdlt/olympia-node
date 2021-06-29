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

import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.constraintmachine.ExecutionContext;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.ProcedureKey;
import com.radixdlt.constraintmachine.REOp;
import com.radixdlt.constraintmachine.exceptions.AuthorizationException;
import com.radixdlt.utils.UInt256;

public final class ResourceFeeMeter implements Meter {
	private final UInt256 perResourceCreationFee;

	private ResourceFeeMeter(UInt256 perResourceCreationFee) {
		this.perResourceCreationFee = perResourceCreationFee;
	}

	public static ResourceFeeMeter create(UInt256 perResourceCreationFee) {
		return new ResourceFeeMeter(perResourceCreationFee);
	}

	@Override
	public void onUserProcedure(ProcedureKey procedureKey, Object param, ExecutionContext context) throws Exception {
		// TODO: Clean this up
		if (procedureKey.opSignature().op() == REOp.UP && param instanceof Particle) {
			var substate = (Particle) param;
			if (substate instanceof TokenResource) {
				context.charge(perResourceCreationFee);
			}
		}
	}

	@Override
	public void onSuperUserProcedure(ProcedureKey procedureKey, Object param, ExecutionContext context) throws Exception {
		// No-op
	}

	@Override
	public void onSigInstruction(ExecutionContext context) throws AuthorizationException {
		 // No-op
	}
}
