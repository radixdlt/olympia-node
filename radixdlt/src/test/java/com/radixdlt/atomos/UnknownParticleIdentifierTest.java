package com.radixdlt.atomos;

import org.junit.Test;
import com.radixdlt.atoms.Particle;
import com.radixdlt.universe.Universe;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

public class UnknownParticleIdentifierTest {
	private static abstract class KnownParticle extends Particle {
	}

	private static abstract class UnknownParticle extends Particle {
	}

	@Test
	public void when_running_constraint_scrypt_with_unknown_particle_identifier__exception_is_thrown() {
		CMAtomOS os = new CMAtomOS(() -> mock(Universe.class), () -> 0);
		assertThatThrownBy(() ->
			os.load(syscalls -> {
				syscalls.registerParticle(KnownParticle.class, "known", (KnownParticle p) -> mock(RadixAddress.class));
				syscalls.on(UnknownParticle.class);
			})
		).isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void when_running_constraint_scrypt_with_known_particle_identifier__exception_is_not_thrown() {
		CMAtomOS os = new CMAtomOS(() -> mock(Universe.class), () -> 0);
		os.load(syscalls -> {
			syscalls.registerParticle(KnownParticle.class, "known", (KnownParticle p) -> mock(RadixAddress.class));
			syscalls.on(KnownParticle.class);
		});
	}
}
