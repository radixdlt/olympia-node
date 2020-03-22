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
import com.radixdlt.common.AID;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.messages.VoteMessage;
import com.radixdlt.crypto.Hash;
import com.radixdlt.utils.Ints;

import static org.mockito.Mockito.mock;

public class VoteMessageSerializeTest extends SerializeMessageObject<VoteMessage> {
	public VoteMessageSerializeTest() {
		super(VoteMessage.class, VoteMessageSerializeTest::get);
	}

	private static VoteMessage get() {
		View parentView = View.of(1234567890L);
		Hash parentId = Hash.random();

		View view = parentView.next();
		Hash id = Hash.random();

		RadixAddress author = RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
		VertexMetadata vertexMetadata = new VertexMetadata(view, id, parentView, parentId);

		Vote vote = new Vote(author.getKey(), vertexMetadata, null);

		return new VoteMessage(1, vote);
	}

	private static AID aidOf(int id) {
		byte[] bytes = new byte[AID.BYTES];
		Ints.copyTo(id, bytes, AID.BYTES - Integer.BYTES);
		return AID.from(bytes);
	}
}
