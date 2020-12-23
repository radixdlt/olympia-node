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

package com.radixdlt.client.application.translate.tokens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.radixdlt.identifiers.RRI;
import java.math.BigDecimal;
import org.junit.Test;

public class TokenOverMintExceptionTest {
	@Test
	public void when_token_over_mint_exception_initialized_with_null__null_pointer_exception_is_thrown() {
		assertThatThrownBy(() -> new TokenOverMintException(null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))
			.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> new TokenOverMintException(mock(RRI.class), null, BigDecimal.ZERO, BigDecimal.ZERO))
			.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> new TokenOverMintException(mock(RRI.class), BigDecimal.ZERO, null, BigDecimal.ZERO))
			.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> new TokenOverMintException(mock(RRI.class), BigDecimal.ZERO, BigDecimal.ZERO, null))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	public void when_token_over_mint_exception_compared_to_similar_big_decimal_exception__equals_returns_true() {
		RRI ref = mock(RRI.class);
		TokenOverMintException e1 = new TokenOverMintException(ref, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1.0"));
		TokenOverMintException e2 = new TokenOverMintException(ref, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1.00000"));
		assertThat(e1).isEqualTo(e2);
	}
}