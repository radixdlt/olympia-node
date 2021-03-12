/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package org.radix.api.services;

import org.radix.api.jsonrpc.AtomStatus;

public enum ApiAtomStatus {
	PENDING,
	CONFIRMED,
	FAILED;


	public static ApiAtomStatus fromAtomStatus(AtomStatus atomStatus) {
		switch (atomStatus) {
			case DOES_NOT_EXIST:
			case EVICTED_FAILED_CM_VERIFICATION:
			case MISSING_DEPENDENCY:
			case MEMPOOL_FULL:
			case MEMPOOL_DUPLICATE:
			case CONFLICT_LOSER:
				return FAILED;
			case PENDING_CM_VERIFICATION:
				return PENDING;
			case STORED:
				return CONFIRMED;
			default:
				throw new IllegalStateException("Unknown atom status " + atomStatus);
		}
	}
}
