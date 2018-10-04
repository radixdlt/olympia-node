package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.AbstractConsumable;
import io.reactivex.Observable;

public interface ParticleStore {
	Observable<AbstractConsumable> getConsumables(RadixAddress address);
}
