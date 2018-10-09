package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.Consumable;
import io.reactivex.Observable;

public interface ParticleStore {
	Observable<Consumable> getConsumables(RadixAddress address);
}
