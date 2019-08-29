package com.radixdlt.constraintmachine;

import java.util.Optional;

public interface UsedCompute<I extends Particle, N extends UsedData, O extends Particle, U extends UsedData> {
	Optional<UsedData> compute(I inputParticle, N inputUsed, O outputParticle, U outputUsed);
}
