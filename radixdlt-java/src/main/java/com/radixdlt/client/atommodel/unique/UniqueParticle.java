package com.radixdlt.client.atommodel.unique;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.quarks.AccountableQuark;
import com.radixdlt.client.atommodel.quarks.NonFungibleQuark;
import com.radixdlt.client.core.atoms.particles.Particle;
import org.radix.serialization2.SerializerId2;

@SerializerId2("UNIQUEIDPARTICLE")
public class UniqueParticle extends Particle {
	private UniqueParticle() {
		super();
	}

	public UniqueParticle(RadixAddress address, String unique) {
		super(new AccountableQuark(address), new NonFungibleQuark(new UniqueId(address, unique)));
	}
}
