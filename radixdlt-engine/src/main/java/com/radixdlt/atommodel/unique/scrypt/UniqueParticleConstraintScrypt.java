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

package com.radixdlt.atommodel.unique.scrypt;

import com.radixdlt.atom.RESerializer;
import com.radixdlt.atommodel.unique.state.UniqueParticle;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.atomos.Loader;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ProcedureException;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.UpProcedure;

import java.util.Set;

public class UniqueParticleConstraintScrypt implements ConstraintScrypt {
	@Override
	public void main(Loader os) {
		os.substate(
			new SubstateDefinition<>(
				UniqueParticle.class,
				Set.of(RESerializer.SubstateType.UNIQUE.id()),
				(b, buf) -> {
					var rri = RESerializer.deserializeREAddr(buf);
					return new UniqueParticle(rri);
				}
			)
		);
		os.procedure(new UpProcedure<>(
			CMAtomOS.REAddrClaim.class, UniqueParticle.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				if (!u.getREAddr().equals(s.getAddr())) {
					throw new ProcedureException("Addresses don't match");
				}
				return ReducerResult.complete();
			}
		));
	}
}
