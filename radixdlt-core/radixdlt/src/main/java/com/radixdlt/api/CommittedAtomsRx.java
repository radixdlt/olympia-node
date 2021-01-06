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

package com.radixdlt.api;

import com.radixdlt.statecomputer.RadixEngineStateComputer.CommittedAtomWithResult;
import io.reactivex.rxjava3.core.Observable;

/**
 * Events related to the ledger
 */
public interface CommittedAtomsRx {
	/**
	 * Retrieve a never ending stream of committed atoms
	 *
	 * @return hot observable of committed commands and it's result
	 */
	Observable<CommittedAtomWithResult> committedAtoms();
}
