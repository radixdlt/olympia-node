package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.atoms.particles.SpunParticle;
import io.reactivex.Observable;

public interface ActionToParticlesMapper {
	Observable<SpunParticle> map(Action action);
}
