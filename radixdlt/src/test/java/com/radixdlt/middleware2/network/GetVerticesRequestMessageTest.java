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
import static org.junit.Assert.assertThat;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;

public class GetVerticesRequestMessageTest {
	@Test
	public void sensibleToString() {
		HashCode vertexId = HashUtils.random256();
		GetVerticesRequestMessage msg1 = new GetVerticesRequestMessage(0, vertexId, 1);
		String s1 = msg1.toString();
		assertThat(s1, containsString(GetVerticesRequestMessage.class.getSimpleName()));
		assertThat(s1, containsString(vertexId.toString()));
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(GetVerticesRequestMessage.class)
				.withIgnoredFields("instance")
				.suppress(Warning.NONFINAL_FIELDS)
				.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
				.verify();
	}

}