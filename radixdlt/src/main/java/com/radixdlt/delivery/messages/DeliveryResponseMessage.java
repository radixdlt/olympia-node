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

package com.radixdlt.delivery.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.store.LedgerEntry;
import org.radix.network.messaging.Message;

@SerializerId2("tempo.sync.delivery.response")
public class DeliveryResponseMessage extends Message {
	@JsonProperty("ledgerEntry")
	@DsonOutput(Output.ALL)
	private LedgerEntry ledgerEntry;

	DeliveryResponseMessage() {
		// For serializer only
		super(0);
	}

	public DeliveryResponseMessage(LedgerEntry ledgerEntry, int magic) {
		super(magic);
		this.ledgerEntry = ledgerEntry;
	}

	public LedgerEntry getLedgerEntry() {
		return ledgerEntry;
	}

	@Override
	public String getCommand() {
		return "tempo.sync.delivery.response";
	}
}
