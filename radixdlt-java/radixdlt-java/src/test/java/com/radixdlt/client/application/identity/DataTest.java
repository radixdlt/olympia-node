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

package com.radixdlt.client.application.identity;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.radixdlt.client.lib.identity.Data;
import com.radixdlt.client.lib.identity.Data.DataBuilder;
import org.junit.Test;

public class DataTest {
	@Test
	public void builderNoBytesTest() {
		DataBuilder dataBuilder = new DataBuilder();
		assertThatThrownBy(dataBuilder::build).isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void builderUnencryptedTest() {
		Data data = new DataBuilder().bytes(new byte[] {}).unencrypted().build();
		assertEquals(0, data.getBytes().length);
		assertNull(data.getEncryptor());
	}

	@Test
	public void builderNoReadersTest() {
		assertThatThrownBy(() -> new DataBuilder().bytes(new byte[] {}).build())
			.isInstanceOf(IllegalStateException.class);
	}
}