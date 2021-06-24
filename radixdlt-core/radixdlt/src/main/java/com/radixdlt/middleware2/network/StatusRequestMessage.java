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
 */

package com.radixdlt.middleware2.network;

import com.radixdlt.serialization.SerializerId2;
import org.radix.network.messaging.Message;

import java.util.Objects;

/**
 * A status request message.
 */
@SerializerId2("message.sync.status_request")
public final class StatusRequestMessage extends Message {

	@Override
	public String toString() {
		return String.format("%s", getClass().getSimpleName());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof StatusRequestMessage)) {
			return false;
		}
		StatusRequestMessage that = (StatusRequestMessage) o;
		return Objects.equals(getTimestamp(), that.getTimestamp());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getTimestamp());
	}
}
