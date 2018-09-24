package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.Consumable;
import io.reactivex.Observable;
import java.util.Collection;

public interface ParticleStore {
	Observable<Collection<Consumable>> getConsumables(RadixAddress address);
}
