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

package com.radixdlt.consensus.bft;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.sync.GetVerticesErrorResponse;
import com.radixdlt.consensus.sync.GetVerticesRequest;

import org.junit.Before;
import org.junit.Test;

public class GetVerticesErrorResponseTest {
	private BFTNode node;
	private HighQC highQC;
	private GetVerticesRequest failedRequest;
	private GetVerticesErrorResponse response;

	@Before
	public void setUp() {
		this.highQC = mock(HighQC.class);
		this.node = mock(BFTNode.class);
		this.failedRequest = mock(GetVerticesRequest.class);
		this.response = new GetVerticesErrorResponse(this.node, this.highQC, this.failedRequest);
	}

	@Test
	public void testGetters() {
		assertThat(this.response.highQC()).isEqualTo(this.highQC);
		assertThat(this.response.getSender()).isEqualTo(this.node);
	}

	@Test
	public void testToString() {
		assertThat(this.response.toString()).isNotNull();
	}

}