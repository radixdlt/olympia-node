package com.radixdlt.client.serialization;

import com.radixdlt.client.atommodel.rri.RRIParticle;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.core.network.jsonrpc.ShardRange;
import com.radixdlt.client.core.network.jsonrpc.ShardSpace;
import java.util.Arrays;
import java.util.Collection;

import com.radixdlt.client.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;

import com.radixdlt.client.application.translate.unique.UniqueId;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.client.atommodel.unique.UniqueParticle;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.ledger.AtomEvent;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.client.core.network.jsonrpc.NodeRunnerData;
import com.radixdlt.client.core.network.jsonrpc.RadixLocalSystem;
import com.radixdlt.client.core.network.jsonrpc.RadixSystem;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationPolicy;
import com.radixdlt.serialization.SerializerIds;

public final class Serialize {

	private static class Holder {
		static final Serialization INSTANCE = Serialization.create(createIds(getClasses()), createPolicy(getClasses()));

		private static SerializerIds createIds(Collection<Class<?>> classes) {
			return CollectionScanningSerializerIds.create(classes);
		}

		private static SerializationPolicy createPolicy(Collection<Class<?>> classes) {
			return CollectionScanningSerializationPolicy.create(classes);
		}

		private static Collection<Class<?>> getClasses() {
			return Arrays.asList(
				Atom.class,
				AtomEvent.class,
				RadixAddress.class,
				ParticleGroup.class,
				Particle.class,
				RRIParticle.class,
				SpunParticle.class,
				MessageParticle.class,
				MutableSupplyTokenDefinitionParticle.class,
				FixedSupplyTokenDefinitionParticle.class,
				UnallocatedTokensParticle.class,
				TransferrableTokensParticle.class,
				UniqueParticle.class,

				ECKeyPair.class,
				ECDSASignature.class,
				NodeRunnerData.class,
				ShardSpace.class,
				ShardRange.class,
				RadixLocalSystem.class,
				RadixSystem.class,
				RadixUniverseConfig.class,
				RRI.class,
				UniqueId.class
			);
		}
	}

	private Serialize() {
		throw new IllegalStateException("Can't construct");
	}

	public static Serialization getInstance() {
		return Holder.INSTANCE;
	}
}
