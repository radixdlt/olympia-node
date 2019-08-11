package com.radixdlt.atommodel.unique;

import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.atomos.ConstraintScrypt;

public class UniqueParticleConstraintScrypt implements ConstraintScrypt {
	@Override
	public void main(SysCalls os) {
		os.registerParticle(
			UniqueParticle.class,
			UniqueParticle::getAddress,
			u -> Result.success()
		);
		os.createTransitionFromRRI(UniqueParticle.class, UniqueParticle::getRRI);
	}
}
