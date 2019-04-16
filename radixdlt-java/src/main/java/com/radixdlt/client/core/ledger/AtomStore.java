package com.radixdlt.client.core.ledger;

import com.radixdlt.client.atommodel.accounts.RadixAddress;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.particles.Particle;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.stream.Stream;

public interface AtomStore {
	Observable<Long> onSync(RadixAddress address);
	Stream<Atom> getAtoms(RadixAddress address);
	Stream<Particle> getUpParticles(RadixAddress address);
	Observable<AtomObservation> getAtomObservations(RadixAddress address);
}
