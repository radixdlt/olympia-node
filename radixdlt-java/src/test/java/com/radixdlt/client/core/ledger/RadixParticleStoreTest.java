package com.radixdlt.client.core.ledger;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import java.util.Collections;
import org.junit.Test;

public class RadixParticleStoreTest {
	@Test
	public void testParticleInstructionWithAtomDelete() {
		RadixAddress address = mock(RadixAddress.class);

		Particle particle = mock(Particle.class);
		when(particle.getShardables()).thenReturn(Collections.singleton(address));

		Atom atom = new Atom(Collections.singletonList(ParticleGroup.of(SpunParticle.up(particle))), 0);
		AtomStore atomStore = mock(AtomStore.class);
		AtomObservation observation = AtomObservation.deleted(atom);

		when(atomStore.getAtoms(eq(address))).thenReturn(Observable.just(observation));
		RadixParticleStore radixParticleStore = new RadixParticleStore(atomStore);

		TestObserver<TransitionedParticle> testObserver = TestObserver.create();
		radixParticleStore.getParticles(address).subscribe(testObserver);
		testObserver.assertValue(t -> t.getTransition().equals(ParticleTransition.U2N));
	}
}