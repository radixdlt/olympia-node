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

package com.radixdlt.network.messaging;

import com.radixdlt.utils.functional.Failure;

public enum MessagingErrors implements Failure {
	MESSAGE_EXPIRED(1, "Message expired"),
	UNKNOWN_PEER(2, "Peer not present in address book"),
	INVALID_SIGNATURE(3, "Invalid signature"),
	NULL_NID(4, "Null NID"),
	INVALID_AGENT_VERSION(5, "Invalid agent version"),
	MESSAGE_FROM_SELF(6, "Message from self");

	private final int code;
	private final String message;

	MessagingErrors(int code, String message) {
		this.code = code;
		this.message = message;
	}

	@Override
	public String message() {
		return message;
	}

	@Override
	public int code() {
		return code;
	}
}
