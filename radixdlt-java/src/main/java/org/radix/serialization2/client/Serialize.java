package org.radix.serialization2.client;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.TokenClassReference;
import com.radixdlt.client.core.atoms.particles.FeeParticle;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.atoms.particles.StorageParticle;
import com.radixdlt.client.core.atoms.particles.TimestampParticle;
import com.radixdlt.client.core.atoms.particles.TokenParticle;
import com.radixdlt.client.core.atoms.particles.TransferParticle;
import com.radixdlt.client.core.atoms.particles.quarks.AddressableQuark;
import com.radixdlt.client.core.atoms.particles.quarks.ChronoQuark;
import com.radixdlt.client.core.atoms.particles.quarks.DataQuark;
import com.radixdlt.client.core.atoms.particles.quarks.FungibleQuark;
import com.radixdlt.client.core.atoms.particles.quarks.NonFungibleQuark;
import com.radixdlt.client.core.atoms.particles.quarks.OwnableQuark;
import com.radixdlt.client.core.atoms.particles.quarks.Quark;
import com.radixdlt.client.core.atoms.particles.quarks.UniqueQuark;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECSignature;
import com.radixdlt.client.core.network.RadixSystem;
import com.radixdlt.client.core.network.TCPNodeRunnerData;
import com.radixdlt.client.core.network.UDPNodeRunnerData;
import org.radix.serialization2.Serialization;
import org.radix.serialization2.SerializationPolicy;
import org.radix.serialization2.SerializerIds;

import java.util.Arrays;
import java.util.Collection;

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
				RadixAddress.class,
				Particle.class,
				SpunParticle.class,
				TimestampParticle.class,
				TransferParticle.class,
				FeeParticle.class,
				StorageParticle.class,
				TokenParticle.class,

				Quark.class,
				ChronoQuark.class,
				DataQuark.class,
				FungibleQuark.class,
				NonFungibleQuark.class,
				OwnableQuark.class,
				UniqueQuark.class,
				AddressableQuark.class,

				ECKeyPair.class,
				ECSignature.class,
				RadixSystem.class,
				RadixUniverseConfig.class,
				TCPNodeRunnerData.class,
				TokenClassReference.class,
				UDPNodeRunnerData.class
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
