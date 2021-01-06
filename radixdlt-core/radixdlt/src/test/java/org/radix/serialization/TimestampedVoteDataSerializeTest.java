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

import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.TimestampedVoteData;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.crypto.HashUtils;

public class TimestampedVoteDataSerializeTest extends SerializeObject<TimestampedVoteData> {
	public TimestampedVoteDataSerializeTest() {
		super(TimestampedVoteData.class, TimestampedVoteDataSerializeTest::get);
	}

	private static TimestampedVoteData get() {
		View view = View.of(1234567890L);
		LedgerHeader ledgerHeader = LedgerHeader.genesis(HashUtils.zero256(), null);
		BFTHeader committed = new BFTHeader(view, HashUtils.random256(), ledgerHeader);
		BFTHeader parent = new BFTHeader(view.next(), HashUtils.random256(), ledgerHeader);
		BFTHeader proposed = new BFTHeader(view.next().next(), HashUtils.random256(), ledgerHeader);
		VoteData voteData = new VoteData(proposed, parent, committed);
		return new TimestampedVoteData(voteData, System.currentTimeMillis());
	}
}
