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

package org.radix.serialization;

import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.messages.NewViewMessage;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.Hash;

public class NewViewMessageSerializeTest extends SerializeMessageObject<NewViewMessage> {
	public NewViewMessageSerializeTest() {
		super(NewViewMessage.class, NewViewMessageSerializeTest::get);
	}

	private static NewViewMessage get() {
		RadixAddress author = RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
		VertexMetadata vertexMetadata = new VertexMetadata(View.of(1), Hash.ZERO_HASH, View.of(2), Hash.ZERO_HASH);
		QuorumCertificate quorumCertificate = new QuorumCertificate(vertexMetadata, new ECDSASignatures());
		NewView testView = new NewView(author.getKey(), View.of(1234567890L), quorumCertificate, null);
		return new NewViewMessage(1234, testView);
	}
}
