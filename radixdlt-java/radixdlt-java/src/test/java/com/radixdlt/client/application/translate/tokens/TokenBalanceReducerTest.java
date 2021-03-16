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

import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.identifiers.RRI;
import java.math.BigDecimal;

import org.junit.Test;
import com.radixdlt.utils.UInt256;

import com.google.common.hash.HashCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TokenBalanceReducerTest {

	@Test
	public void testSimpleBalance() {
		TransferrableTokensParticle minted = mock(TransferrableTokensParticle.class);
		HashCode hash = mock(HashCode.class);
		when(minted.getAmount()).thenReturn(UInt256.TEN);
		when(minted.getGranularity()).thenReturn(UInt256.ONE);
		when(minted.getHash()).thenReturn(hash);
		RRI token = mock(RRI.class);
		when(minted.getTokenDefinitionReference()).thenReturn(token);

		TokenBalanceReducer reducer = new TokenBalanceReducer();
		TokenBalanceState tokenBalance = reducer.reduce(new TokenBalanceState(), minted);
		BigDecimal tenSubunits = TokenUnitConversions.subunitsToUnits(UInt256.TEN);
		assertThat(tokenBalance.getBalance().get(token).compareTo(tenSubunits)).isEqualTo(0);
	}
}