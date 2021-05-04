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

package org.radix.network.messages;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;
import org.radix.serialization.SerializeMessageObject;

import static org.assertj.core.api.Assertions.assertThat;

public class GetPeersResponseMessageTest extends SerializeMessageObject<GetPeersMessage> {

	public GetPeersResponseMessageTest() {
		super(GetPeersMessage.class, GetPeersMessage::new);
	}

	@Test
	public void sensibleToString() {
		String s = new GetPeersMessage().toString();

		assertThat(s).contains(GetPeersMessage.class.getSimpleName());
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(GetPeersMessage.class)
				.withIgnoredFields("instance")
				.suppress(Warning.NONFINAL_FIELDS)
				.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
				.verify();
	}

}
