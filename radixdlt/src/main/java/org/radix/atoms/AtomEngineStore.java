package org.radix.atoms;

import com.radixdlt.middleware.RadixEngineUtils;
import com.radixdlt.middleware.RadixEngineUtils.CMAtomConversionException;
import com.radixdlt.middleware.SimpleRadixEngineAtom;
import com.radixdlt.utils.UInt384;
import java.util.Set;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;
import com.radixdlt.atoms.SpunParticle;
import java.util.function.Consumer;
import org.radix.atoms.events.AtomExceptionEvent;
import org.radix.database.exceptions.DatabaseException;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.StateStoreException;
import com.radixdlt.common.EUID;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.radix.events.Events;
import org.radix.shards.ShardSpace;

/**
 * A state store that uses an {@link AtomStore} to provide relevant shard state
 */
public class AtomEngineStore implements EngineStore<SimpleRadixEngineAtom> {
	private final Supplier<AtomStore> atomStoreSupplier;
	private final Supplier<ShardSpace> shardSpaceSupplier;

	public AtomEngineStore(Supplier<AtomStore> atomStoreSupplier, Supplier<ShardSpace> shardSpaceSupplier) {
		Objects.requireNonNull(atomStoreSupplier, "atomStoreSupplier is required");
		Objects.requireNonNull(shardSpaceSupplier);

		this.atomStoreSupplier = atomStoreSupplier;
		this.shardSpaceSupplier = shardSpaceSupplier;
	}

	@Override
	public void getAtomContaining(SpunParticle spunParticle, Consumer<SimpleRadixEngineAtom> callback) {
		try {
			// cheap early out in case the spun particle is not even in the store
			if (!atomStoreSupplier.get().hasAtomContaining(spunParticle.getParticle(), spunParticle.getSpin())) {
				callback.accept(null);
				return;
			}

			Atom atom = atomStoreSupplier.get().getAtomContaining(spunParticle.getParticle(), spunParticle.getSpin());
			if (atom == null) {
				callback.accept(null);
			} else {
				callback.accept(RadixEngineUtils.toCMAtom(atom));
			}
		} catch (DatabaseException | CMAtomConversionException e) {
			throw new StateStoreException("Discovery for " + spunParticle + " failed: " + e, e);
		}
	}

	@Override
	public boolean supports(Set<EUID> physicalShards) {
		return shardSpaceSupplier.get().intersects(physicalShards.stream().map(EUID::getShard).collect(Collectors.toSet()));
	}

	@Override
	public Spin getSpin(Particle particle) {
		try {
			return atomStoreSupplier.get().getSpin(particle);
		} catch (DatabaseException dex) {
			throw new StateStoreException("Discovery for " + particle.getClass() + " with " + particle.getHID() + " failed: " + dex, dex);
		}
	}

	@Override
	public void storeAtom(SimpleRadixEngineAtom cmAtom) {
		try {
			final PreparedAtom preparedAtom = new PreparedAtom(cmAtom, UInt384.TEN);
			atomStoreSupplier.get().storeAtom(preparedAtom);
		} catch (Exception e) {
			AtomExceptionEvent atomExceptionEvent = new AtomExceptionEvent(e, (Atom) cmAtom.getAtom());
			Events.getInstance().broadcast(atomExceptionEvent);
		}
	}

	@Override
	public void deleteAtom(SimpleRadixEngineAtom cmAtom) {
		try {
			atomStoreSupplier.get().deleteAtoms((Atom) cmAtom.getAtom());
		} catch (DatabaseException dex) {
			throw new StateStoreException("Could not delete atom", dex);
		}
	}
}
