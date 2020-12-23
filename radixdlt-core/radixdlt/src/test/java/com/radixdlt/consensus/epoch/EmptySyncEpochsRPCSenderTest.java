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

package com.radixdlt.consensus.epoch;

import static org.mockito.Mockito.mock;

import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import org.junit.Test;

public class EmptySyncEpochsRPCSenderTest {
	@Test
	public void when_send_request_and_response__then_no_exception_occurs() {
		EmptySyncEpochsRPCSender.INSTANCE.sendGetEpochRequest(mock(BFTNode.class), 12345L);
		EmptySyncEpochsRPCSender.INSTANCE.sendGetEpochResponse(mock(BFTNode.class), mock(VerifiedLedgerHeaderAndProof.class));
	}

}