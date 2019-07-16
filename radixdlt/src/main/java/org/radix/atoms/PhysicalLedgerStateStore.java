package org.radix.atoms;

import java.util.Optional;
import java.util.Set;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;
import com.radixdlt.atoms.SpunParticle;
import org.radix.database.exceptions.DatabaseException;
import com.radixdlt.store.StateStore;
import com.radixdlt.store.StateStoreException;
import com.radixdlt.common.EUID;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.radix.shards.ShardSpace;

/**
 * A state store that uses an {@link AtomStore} to provide relevant shard state
 */
public class PhysicalLedgerStateStore implements StateStore {
	private final Supplier<AtomStore> atomStoreSupplier;
	private final Supplier<ShardSpace> shardSpaceSupplier;

	public PhysicalLedgerStateStore(Supplier<AtomStore> atomStoreSupplier, Supplier<ShardSpace> shardSpaceSupplier) {
		Objects.requireNonNull(atomStoreSupplier, "atomStoreSupplier is required");
		Objects.requireNonNull(shardSpaceSupplier);

		this.atomStoreSupplier = atomStoreSupplier;
		this.shardSpaceSupplier = shardSpaceSupplier;
	}

	public Atom getAtomContaining(SpunParticle spunParticle) {
		try {
			// cheap early out in case the spun particle is not even in the store
			if (!atomStoreSupplier.get().hasAtomContaining(spunParticle.getParticle(), spunParticle.getSpin())) {
				return null;
			}

			return atomStoreSupplier.get().getAtomContaining(spunParticle.getParticle(), spunParticle.getSpin());

		} catch (DatabaseException dex) {
			throw new StateStoreException("Discovery for " + spunParticle + " failed: " + dex, dex);
		}
	}

	@Override
	public boolean supports(Set<EUID> physicalShards) {
		return shardSpaceSupplier.get().intersects(physicalShards.stream().map(EUID::getShard).collect(Collectors.toSet()));
	}

	@Override
	public Optional<Spin> getSpin(Particle particle) {
		try {
			return Optional.of(atomStoreSupplier.get().getSpin(particle));
		} catch (DatabaseException dex) {
			throw new StateStoreException("Discovery for " + particle.getClass() + " with " + particle.getHID() + " failed: " + dex, dex);
		}
	}

}
