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

package com.radixdlt.client.application.translate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.crypto.Hash;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.client.core.pow.ProofOfWork;
import com.radixdlt.client.core.pow.ProofOfWorkBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.radixdlt.utils.Pair;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PowFeeMapperTest {
	@Test
	public void testNormalMap() {
		Hash hash = mock(Hash.class);
		when(hash.toByteArray()).thenReturn(new byte[] {});
		ProofOfWorkBuilder builder = mock(ProofOfWorkBuilder.class);
		ProofOfWork pow = mock(ProofOfWork.class);
		when(builder.build(anyInt(), any(), anyInt())).thenReturn(pow);
		when(pow.getNonce()).thenReturn(1L);

		Function<Atom, Hash> hasher = mock(Function.class);
		when(hasher.apply(any())).thenReturn(hash);
		PowFeeMapper powFeeMapper = new PowFeeMapper(hasher, builder);

		RadixUniverse universe = mock(RadixUniverse.class);

		ECPublicKey key = mock(ECPublicKey.class);
		RadixAddress address = mock(RadixAddress.class);
		when(address.getPublicKey()).thenReturn(key);
		when(universe.getAddressFrom(key)).thenReturn(address);

		Pair<Map<String, String>, List<ParticleGroup>> output = powFeeMapper.map(Atom.create(Collections.emptyList(), 0L), universe, key);
		assertThat(output.getFirst()).containsOnlyKeys(Atom.METADATA_POW_NONCE_KEY);

		verify(builder, times(1)).build(anyInt(), any(), anyInt());
		verify(hasher, times(1)).apply(any());
	}

}
