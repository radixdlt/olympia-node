/*
 * (C) Copyright 2020 Radix DLT Ltd
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
 */

package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.atomos.Result;
import com.radixdlt.engine.AtomChecker;
import com.radixdlt.atom.LedgerAtom;

/**
 * Module which provides an atom checker that does not require fees.
 */
public class NoFeeModule extends AbstractModule {
	@Provides
	@Singleton
	private AtomChecker<LedgerAtom> noFeeLedgerAtomChecker() {
		return (atom, permissionLevel) ->
			atom.getCMInstruction().getMicroInstructions().isEmpty()
				? Result.error("atom has no instructions")
				: Result.success();
	}
}
