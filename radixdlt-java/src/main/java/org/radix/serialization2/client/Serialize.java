package org.radix.serialization2.client;

import java.util.Arrays;
import java.util.Collection;

import org.radix.serialization2.Serialization;
import org.radix.serialization2.SerializationPolicy;
import org.radix.serialization2.SerializerIds;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.atoms.AccountReference;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.TokenRef;
import com.radixdlt.client.core.atoms.particles.AtomFeeConsumable;
import com.radixdlt.client.core.atoms.particles.ChronoParticle;
import com.radixdlt.client.core.atoms.particles.Consumable;
import com.radixdlt.client.core.atoms.particles.DataParticle;
import com.radixdlt.client.core.atoms.particles.TokenParticle;
import com.radixdlt.client.core.atoms.particles.UniqueParticle;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECSignature;
import com.radixdlt.client.core.network.RadixSystem;
import com.radixdlt.client.core.network.TCPNodeRunnerData;
import com.radixdlt.client.core.network.UDPNodeRunnerData;

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
				AccountReference.class,
				Atom.class,
				AtomFeeConsumable.class,
				ChronoParticle.class,
				Consumable.class,
				DataParticle.class,
				ECKeyPair.class,
				ECSignature.class,
				RadixSystem.class,
				RadixUniverseConfig.class,
				TCPNodeRunnerData.class,
				TokenParticle.class,
				TokenRef.class,
				UDPNodeRunnerData.class,
				UniqueParticle.class
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
