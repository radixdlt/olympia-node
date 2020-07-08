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

import com.radixdlt.consensus.VoteData;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.Vote;
import com.radixdlt.crypto.Hash;

public class VoteSerializeTest extends SerializeObject<Vote> {
	public VoteSerializeTest() {
		super(Vote.class, VoteSerializeTest::get);
	}

	private static Vote get() {
		View view = View.of(1234567891L);
		Hash id = Hash.random();

		VertexMetadata vertexMetadata = new VertexMetadata(0, view, id, 1, false);
		VertexMetadata parent = new VertexMetadata(0, View.of(1234567890L), Hash.random(), 0, false);
		VoteData voteData = new VoteData(vertexMetadata, parent, null);
		RadixAddress author = RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
		return new Vote(author.getPublicKey(), voteData, null);
	}
}
