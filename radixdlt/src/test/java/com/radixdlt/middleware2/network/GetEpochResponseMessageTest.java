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

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.crypto.ECPublicKey;
import org.junit.Test;

public class GetEpochResponseMessageTest {
	@Test
	public void sensibleToString() {
		VertexMetadata ancestor = mock(VertexMetadata.class);
		GetEpochResponseMessage msg = new GetEpochResponseMessage(mock(ECPublicKey.class), 12345, ancestor);
		String s1 = msg.toString();
		assertThat(s1, containsString(GetEpochResponseMessage.class.getSimpleName()));
	}
}