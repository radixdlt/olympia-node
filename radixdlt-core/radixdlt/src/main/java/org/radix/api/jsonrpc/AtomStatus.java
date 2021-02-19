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

package org.radix.api.jsonrpc;

public enum AtomStatus {
	DOES_NOT_EXIST,
	EVICTED_FAILED_CM_VERIFICATION,
	EVICTED_CONFLICT_LOSER,
	PENDING_CM_VERIFICATION,
	PENDING_DEPENDENCY_VERIFICATION,
	MISSING_DEPENDENCY,
	CONFLICT_LOSER,
	MEMPOOL_FULL,
	MEMPOOL_DUPLICATE,
	STORED
}
