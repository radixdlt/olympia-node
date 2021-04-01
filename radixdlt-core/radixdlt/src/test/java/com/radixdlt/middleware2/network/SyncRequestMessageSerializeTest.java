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

import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.ledger.AccumulatorState;
import org.radix.serialization.SerializeMessageObject;

public class SyncRequestMessageSerializeTest extends SerializeMessageObject<SyncRequestMessage> {
	public SyncRequestMessageSerializeTest() {
		super(SyncRequestMessage.class, SyncRequestMessageSerializeTest::get);
	}

	private static SyncRequestMessage get() {
		var accumulatorState = new AccumulatorState(0, HashUtils.zero256());
		return new SyncRequestMessage(1234, LedgerProof.genesis(accumulatorState, null).toDto());
	}

}
