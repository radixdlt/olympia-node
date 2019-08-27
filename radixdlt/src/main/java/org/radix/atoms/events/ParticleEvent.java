package org.radix.atoms.events;

import com.radixdlt.middleware.SpunParticle;
import org.radix.events.Event;

public abstract class ParticleEvent extends Event {
	private final SpunParticle particle;
	
	public ParticleEvent(SpunParticle particle) {
		super();
		
		this.particle = particle;
	}

	public SpunParticle getSpunParticle() {
		return particle; 
	}
}
