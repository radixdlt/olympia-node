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

package com.radixdlt.client.store;

import org.json.JSONObject;

import java.util.Objects;

import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

import static java.util.Objects.requireNonNull;

public class MessageEntry {
	private static final String PLAIN = "text/unencrypted";

	private final String message;
	private final String encryptionScheme;

	private MessageEntry(String message, String encryptionScheme) {
		this.message = message;
		this.encryptionScheme = encryptionScheme;
	}

	public static MessageEntry create(String message, String encryptionScheme) {
		requireNonNull(message);
		requireNonNull(encryptionScheme);

		return new MessageEntry(message, encryptionScheme);
	}

	public static MessageEntry fromPlainString(String message) {
		requireNonNull(message);

		return create(message, PLAIN);
	}

	public JSONObject asJson() {
		return jsonObject().put("msg", message).put("encryptionScheme", encryptionScheme);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (o instanceof MessageEntry) {
			var that = (MessageEntry) o;
			return Objects.equals(message, that.message) && Objects.equals(encryptionScheme, that.encryptionScheme);
		}

		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(message, encryptionScheme);
	}

	@Override
	public String toString() {
		return asJson().toString(2);
	}
}
