package com.radixdlt.client.application.translate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.application.translate.tokens.TokenClassReference;
import com.radixdlt.client.atommodel.tokens.FeeParticle;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.pow.ProofOfWork;
import com.radixdlt.client.core.pow.ProofOfWorkBuilder;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.junit.Test;

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

		Function<List<SpunParticle>, RadixHash> hasher = mock(Function.class);
		when(hasher.apply(any())).thenReturn(hash);
		PowFeeMapper powFeeMapper = new PowFeeMapper(hasher, builder);

		RadixUniverse universe = mock(RadixUniverse.class);
		TokenClassReference powToken = mock(TokenClassReference.class);
		when(powToken.getAddress()).thenReturn(mock(RadixAddress.class));
		when(powToken.getSymbol()).thenReturn("POW");
		when(universe.getPOWToken()).thenReturn(powToken);

		ECPublicKey key = mock(ECPublicKey.class);
		RadixAddress address = mock(RadixAddress.class);
		when(address.getPublicKey()).thenReturn(key);
		when(universe.getAddressFrom(key)).thenReturn(address);

		List<SpunParticle> particles = powFeeMapper.map(Collections.emptyList(), universe, key);
		assertThat(particles)
				.hasOnlyOneElementSatisfying(s -> {
					assertThat(s.getParticle()).isInstanceOf(FeeParticle.class);
					FeeParticle a = (FeeParticle) s.getParticle();
					assertThat(a.getTokenClassReference()).isEqualTo(powToken);
				});

		verify(builder, times(1)).build(anyInt(), any(), anyInt());
		verify(hasher, times(1)).apply(any());
	}

}