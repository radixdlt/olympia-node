/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.application.translate.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.crypto.ECKeyPair;
import org.junit.Test;
import com.radixdlt.identifiers.EUID;

public class SendDecryptedMessageToParticleGroupsMapperTest {
	@Test
	public void testNoEncryption() {
		RadixAddress address = mock(RadixAddress.class);
		when(address.euid()).thenReturn(mock(EUID.class), mock(EUID.class));

		SendMessageToParticleGroupsMapper sendMessageToParticleGroupsMapper =
			new SendMessageToParticleGroupsMapper(() -> mock(ECKeyPair.class));
		SendMessageAction sendMessageAction = mock(SendMessageAction.class);
		when(sendMessageAction.getData()).thenReturn(new byte[] {});
		when(sendMessageAction.getFrom()).thenReturn(address);
		when(sendMessageAction.getTo()).thenReturn(address);
		when(sendMessageAction.encrypt()).thenReturn(false);
		assertThat(sendMessageToParticleGroupsMapper.mapToParticleGroups(sendMessageAction)).hasSize(1);
	}
}