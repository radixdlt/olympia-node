package com.radixdlt.atommodel.unique;

import com.radixdlt.atomos.AtomOS;
import com.radixdlt.atomos.ConstraintScrypt;

public class UniqueParticleConstraintScrypt implements ConstraintScrypt {
	@Override
	public void main(AtomOS os) {
		os.registerParticle(UniqueParticle.class, UniqueParticle::getAddress);
		os.newResource(UniqueParticle.class, UniqueParticle::getRRI);
	}
}
