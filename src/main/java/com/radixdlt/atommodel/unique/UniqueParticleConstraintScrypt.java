package com.radixdlt.atommodel.unique;

import com.radixdlt.atomos.SysCalls;
import com.radixdlt.atomos.ConstraintScrypt;

public class UniqueParticleConstraintScrypt implements ConstraintScrypt {
	@Override
	public void main(SysCalls os) {
		os.registerParticle(UniqueParticle.class, UniqueParticle::getAddress);
		os.newResourceType(UniqueParticle.class, UniqueParticle::getRRI);
	}
}
