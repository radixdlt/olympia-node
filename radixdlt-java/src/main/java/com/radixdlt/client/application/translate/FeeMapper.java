package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.crypto.ECPublicKey;

import java.util.List;

public interface FeeMapper {
	List<ParticleGroup> map(List<ParticleGroup> particles, RadixUniverse universe, ECPublicKey key);
}
