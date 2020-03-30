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

package com.radixdlt.middleware2.converters;

import com.radixdlt.atommodel.Atom;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;

public final class AtomToBinaryConverter {
	private final Serialization serializer;

	public AtomToBinaryConverter(Serialization serializer) {
		this.serializer = serializer;
	}

	public byte[] toLedgerEntryContent(Atom atom) {
		try {
			return serializer.toDson(atom, DsonOutput.Output.PERSIST);
		} catch (SerializationException e) {
			throw new RuntimeException(String.format("Serialization for Atom with ID: %s failed", atom.getAID()));
		}
	}

	public Atom toAtom(byte[] ledgerEntryContent) {
		try {
			return serializer.fromDson(ledgerEntryContent, Atom.class);
		} catch (SerializationException e) {
			throw new RuntimeException("Deserialization of Atom failed");
		}
	}
}
