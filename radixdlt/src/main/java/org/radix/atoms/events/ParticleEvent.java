package org.radix.atoms.events;

import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.SpunParticle;
import org.radix.events.Event;

public abstract class ParticleEvent extends Event 
{ 
	private final SpunParticle<? extends Particle> particle;
	
	public ParticleEvent(SpunParticle<? extends Particle> particle)
	{
		super();
		
		this.particle = particle;
	}

	public SpunParticle getSpunParticle()
	{ 
		return particle; 
	}
}
