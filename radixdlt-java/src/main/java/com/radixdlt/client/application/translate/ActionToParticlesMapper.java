package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.atoms.particles.SpunParticle;
import io.reactivex.Observable;

/**
 * Maps a high level application action to lower level spun particles used
 * to construct an atom.
 */
public interface ActionToParticlesMapper {
	Observable<SpunParticle> map(Action action);
}
