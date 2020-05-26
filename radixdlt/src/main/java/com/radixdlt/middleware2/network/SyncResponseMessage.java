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

package com.radixdlt.middleware2.network;

import com.google.common.collect.ImmutableList;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.serialization.SerializerId2;
import java.util.List;
import org.radix.network.messaging.Message;

@SerializerId2("message.sync.response")
public class SyncResponseMessage extends Message {
	private final ImmutableList<CommittedAtom> atoms;

	SyncResponseMessage() {
		// Serializer only
		super(0);
		this.atoms = null;
	}

	public SyncResponseMessage(int magic, ImmutableList<CommittedAtom> atoms) {
		super(magic);
		this.atoms = atoms;
	}

	public List<CommittedAtom> getAtoms() {
		return atoms;
	}
}
