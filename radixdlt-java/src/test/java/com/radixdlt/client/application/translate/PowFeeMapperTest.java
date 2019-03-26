package com.radixdlt.client.application.translate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.pow.ProofOfWork;
import com.radixdlt.client.core.pow.ProofOfWorkBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.Test;
import org.radix.common.tuples.Pair;

import static org.assertj.core.api.Assertions.assertThat;

public class PowFeeMapperTest {
	@Test
	public void testNormalMap() {
		RadixHash hash = mock(RadixHash.class);
		when(hash.toByteArray()).thenReturn(new byte[] {});
		ProofOfWorkBuilder builder = mock(ProofOfWorkBuilder.class);
		ProofOfWork pow = mock(ProofOfWork.class);
		when(builder.build(anyInt(), any(), anyInt())).thenReturn(pow);
		when(pow.getNonce()).thenReturn(1L);

		Function<Atom, RadixHash> hasher = mock(Function.class);
		when(hasher.apply(any())).thenReturn(hash);
		PowFeeMapper powFeeMapper = new PowFeeMapper(hasher, builder);

		RadixUniverse universe = mock(RadixUniverse.class);
		TokenDefinitionReference powToken = mock(TokenDefinitionReference.class);
		when(powToken.getAddress()).thenReturn(mock(RadixAddress.class));
		when(powToken.getSymbol()).thenReturn("POW");
		when(universe.getPOWToken()).thenReturn(powToken);

		ECPublicKey key = mock(ECPublicKey.class);
		RadixAddress address = mock(RadixAddress.class);
		when(address.getPublicKey()).thenReturn(key);
		when(universe.getAddressFrom(key)).thenReturn(address);

		Pair<Map<String, String>, List<ParticleGroup>> output = powFeeMapper.map(new Atom(Collections.emptyList(), 0L), universe, key);
		assertThat(output.getFirst()).containsOnlyKeys(Atom.METADATA_POW_NONCE_KEY);

		verify(builder, times(1)).build(anyInt(), any(), anyInt());
		verify(hasher, times(1)).apply(any());
	}

}