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

import com.radixdlt.common.AID;
import com.radixdlt.consensus.Round;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.utils.Ints;

public class VertexMetadataSerializeTest extends SerializeObject<VertexMetadata> {
	public VertexMetadataSerializeTest() {
		super(VertexMetadata.class, VertexMetadataSerializeTest::get);
	}

	private static VertexMetadata get() {
		Round parentRound = Round.of(1234567890L);
		Round round = parentRound.next();
		AID parentAid = aidOf(12345);
		AID aid = aidOf(23456);

		return new VertexMetadata(round, aid, parentRound, parentAid);
	}

	private static AID aidOf(int id) {
		byte[] bytes = new byte[AID.BYTES];
		Ints.copyTo(id, bytes, AID.BYTES - Integer.BYTES);
		return AID.from(bytes);
	}
}
