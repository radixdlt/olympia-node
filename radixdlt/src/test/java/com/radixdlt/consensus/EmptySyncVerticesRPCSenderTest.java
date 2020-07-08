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

package com.radixdlt.consensus;

import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.bft.VertexStore.GetVerticesRequest;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import org.junit.Test;

public class EmptySyncVerticesRPCSenderTest {
	@Test
	public void when_execute_methods_in_empty__then_should_do_nothing() {
		EmptySyncVerticesRPCSender.INSTANCE
			.sendGetVerticesResponse(mock(GetVerticesRequest.class), ImmutableList.of());
		EmptySyncVerticesRPCSender.INSTANCE
			.sendGetVerticesErrorResponse(mock(GetVerticesRequest.class), mock(QuorumCertificate.class), mock(QuorumCertificate.class));
		EmptySyncVerticesRPCSender.INSTANCE
			.sendGetVerticesRequest(mock(Hash.class), mock(ECPublicKey.class), 1, mock(Object.class));
	}
}