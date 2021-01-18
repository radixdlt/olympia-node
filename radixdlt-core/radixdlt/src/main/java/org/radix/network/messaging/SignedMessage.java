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

package org.radix.network.messaging;

import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;

import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class SignedMessage extends Message {

	@JsonProperty("signature")
	@DsonOutput(value = { Output.HASH }, include = false)
	private ECDSASignature signature;

	@Override
	public short VERSION() {
		return 100;
	}

	protected SignedMessage(int magic) {
		super(magic);
	}

	protected SignedMessage(int magic, long timestamp) {
		super(magic, timestamp);
	}

	// SIGNABLE //
	public final ECDSASignature getSignature() {
		return this.signature;
	}

	public final void setSignature(ECDSASignature signature) {
		this.signature = signature;
	}

	public boolean verify(ECPublicKey key, Hasher hasher) {
		return key.verify(hasher.hash(this), getSignature());
	}
}
