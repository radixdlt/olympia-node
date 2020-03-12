package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.Pair;

import java.util.List;
import java.util.Map;

public interface FeeMapper {
	// TODO maybe all Mappers should be able to return metadata as well..?
	Pair<Map<String, String>, List<ParticleGroup>> map(Atom atom, RadixUniverse universe, ECPublicKey key);
}
