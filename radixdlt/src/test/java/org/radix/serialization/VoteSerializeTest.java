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
import com.radixdlt.common.EUID;
import com.radixdlt.consensus.Round;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.Vote;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.Ints;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class VoteSerializeTest extends SerializeObject<Vote> {
	public VoteSerializeTest() {
		super(Vote.class, VoteSerializeTest::get);
	}

	private static Vote get() {
		Round parentRound = Round.of(1234567890L);
		AID parentAid = aidOf(23456);

		Round round = parentRound.next();
		AID aid = aidOf(123456);


		VertexMetadata vertexMetadata = new VertexMetadata(round, aid, parentRound, parentAid);
		RadixAddress author = RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
		return new Vote(author, vertexMetadata, null);
	}

	private static AID aidOf(int id) {
		byte[] bytes = new byte[AID.BYTES];
		Ints.copyTo(id, bytes, AID.BYTES - Integer.BYTES);
		return AID.from(bytes);
	}
}
