package com.radixdlt.client.core.ledger;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import io.reactivex.Observable;

public interface ParticleStore {
	Observable<ParticleObservation> getParticles(RadixAddress address);
}
