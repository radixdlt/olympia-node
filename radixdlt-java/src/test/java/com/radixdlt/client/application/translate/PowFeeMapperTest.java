package com.radixdlt.client.application.translate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.TokenRef;
import com.radixdlt.client.core.atoms.particles.AtomFeeConsumable;
import com.radixdlt.client.core.atoms.particles.Particle;
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

		Function<List<Particle>, RadixHash> hasher = mock(Function.class);
		when(hasher.apply(any())).thenReturn(hash);
		PowFeeMapper powFeeMapper = new PowFeeMapper(hasher, builder);

		RadixUniverse universe = mock(RadixUniverse.class);
		TokenRef powToken = mock(TokenRef.class);
		when(universe.getPOWToken()).thenReturn(powToken);

		List<Particle> particles = powFeeMapper.map(Collections.emptyList(), universe, mock(ECPublicKey.class));
		assertThat(particles)
			.hasOnlyOneElementSatisfying(p -> {
				assertThat(p).isInstanceOf(AtomFeeConsumable.class);
				AtomFeeConsumable a = (AtomFeeConsumable) p;
				assertThat(a.getTokenRef()).isEqualTo(powToken);
			});

		verify(builder, times(1)).build(anyInt(), any(), anyInt());
		verify(hasher, times(1)).apply(any());
	}

}