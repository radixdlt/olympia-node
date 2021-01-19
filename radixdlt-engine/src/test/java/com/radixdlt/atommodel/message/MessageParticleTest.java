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

package com.radixdlt.atommodel.message;

import static org.mockito.Mockito.mock;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.RadixAddress;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class MessageParticleTest {
	@Test
	public void when_constructing_message_particle_without_source__exception_is_thrown() {
		RadixAddress from = mock(RadixAddress.class);
		Assertions.assertThatThrownBy(() ->
				new MessageParticle(null, from, new byte[0]))
				.isInstanceOf(NullPointerException.class);

		Assertions.assertThatThrownBy(() ->
				new MessageParticle(null, from, new byte[0], ""))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(MessageParticle.class)
				.suppress(Warning.NONFINAL_FIELDS)
				.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
				.verify();
	}
}