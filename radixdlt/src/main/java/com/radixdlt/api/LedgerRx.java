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

import io.reactivex.rxjava3.core.Observable;

/**
 * Events related to the ledger
 */
public interface LedgerRx {

	/**
	 * Retrieve a never ending stream of conflict
	 * exceptions
	 *
	 * @return hot observable of conflict exceptions
	 */
	Observable<ConflictException> conflictExceptions();

	/**
	 * Retrieve a never ending stream of stored atoms
	 *
	 * @return hot observable of stored atoms
	 */
	Observable<StoredAtom> storedAtoms();
}
