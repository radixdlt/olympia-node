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

package com.radixdlt.ledger;

/**
 * Exception which suggests that there exists a byzantine quorum which
 * got us to this exception state.
 *
 * TODO: Remove all instance of this class and replace with mechanism to
 * log and revert to last known good state.
 */
public class ByzantineQuorumException extends RuntimeException {
	public ByzantineQuorumException(String message) {
		super(message);
	}

	public ByzantineQuorumException(String message, Exception cause) {
		super(message, cause);
	}
}
