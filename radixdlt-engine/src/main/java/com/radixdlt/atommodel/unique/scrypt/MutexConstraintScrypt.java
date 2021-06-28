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

import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.Loader;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.EndProcedure;
import com.radixdlt.constraintmachine.PermissionLevel;

public class MutexConstraintScrypt implements ConstraintScrypt {
	@Override
	public void main(Loader os) {
		os.procedure(new EndProcedure<>(
			CMAtomOS.REAddrClaim.class,
			s -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, c, r) -> { }
		));
	}
}
