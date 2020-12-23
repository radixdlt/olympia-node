/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package org.radix.universe.system;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import org.radix.network.messaging.SignedMessage;

import java.util.Objects;

@SerializerId2("system")
public class SystemMessage extends SignedMessage {
	@JsonProperty("system")
	@DsonOutput(Output.ALL)
	private RadixSystem system;

	protected SystemMessage() {
		// for serializer
		super(0);
	}

	public SystemMessage(RadixSystem system, int magic) {
		super(magic);
		this.system = system;
	}

	public RadixSystem getSystem() {
		return this.system;
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), this.system.getNID());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		SystemMessage that = (SystemMessage) o;
		return Objects.equals(system, that.system)
				&& Objects.equals(getSignature(), that.getSignature())
				&& Objects.equals(getTimestamp(), that.getTimestamp())
				&& Objects.equals(getMagic(), that.getMagic());
	}

	@Override
	public int hashCode() {
		return Objects.hash(system, getSignature(), getTimestamp(), getMagic());
	}
}
