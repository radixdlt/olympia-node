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

import com.google.common.collect.ImmutableSet;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.RadixAddress;
import org.junit.Before;
import org.junit.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MessageParticleConstraintScryptTest {
	private Function<Particle, Result> staticCheck;

	@Before
	public void initializeConstraintScrypt() {
		MessageParticleConstraintScrypt tokensConstraintScrypt = new MessageParticleConstraintScrypt();
		CMAtomOS cmAtomOS = new CMAtomOS();
		cmAtomOS.load(tokensConstraintScrypt);
		staticCheck = cmAtomOS.buildParticleStaticCheck();
	}

	private static MessageParticle makeMessage(byte[] bytes, RadixAddress from, RadixAddress to) {
		MessageParticle messageParticle = mock(MessageParticle.class);
		when(messageParticle.getBytes()).thenReturn(bytes);
		when(messageParticle.getFrom()).thenReturn(from);
		when(messageParticle.getTo()).thenReturn(to);
		when(messageParticle.getAddresses()).thenReturn(ImmutableSet.of(mock(RadixAddress.class)));
		return messageParticle;
	}

	@Test
	public void when_checking_message_without_bytes__result_is_error() {
		MessageParticle message = makeMessage(null, mock(RadixAddress.class), mock(RadixAddress.class));
		assertThat(staticCheck.apply(message).getErrorMessage()).contains("data");
	}

	@Test
	public void when_checking_message_without_from__result_is_error() {
		MessageParticle message = makeMessage(new byte[10], null, mock(RadixAddress.class));
		assertThat(staticCheck.apply(message).getErrorMessage()).contains("from");
	}

	@Test
	public void when_checking_message_without_to__result_is_error() {
		MessageParticle message = makeMessage(new byte[10], mock(RadixAddress.class), null);
		assertThat(staticCheck.apply(message).getErrorMessage()).contains("to");
	}
}
