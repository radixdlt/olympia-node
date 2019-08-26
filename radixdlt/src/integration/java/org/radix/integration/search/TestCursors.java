package org.radix.integration.search;

import com.radixdlt.atomos.SimpleRadixEngineAtom;
import com.radixdlt.engine.RadixEngineAtom;
import com.radixdlt.atomos.RadixEngineUtils;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.UInt384;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.radixdlt.common.AID;
import org.junit.Test;
import org.radix.atoms.AtomDiscoveryRequest;
import org.radix.atoms.AtomStore;
import org.radix.atoms.PreparedAtom;
import org.radix.discovery.DiscoveryRequest.Action;
import org.radix.integration.RadixTestWithStores;
import org.radix.modules.Modules;
import org.radix.time.TemporalVertex;
import org.radix.time.Time;
import org.radix.universe.system.LocalSystem;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Longs;
import com.radixdlt.atommodel.message.MessageParticle;
import com.radixdlt.atomos.RadixAddress;
import org.radix.atoms.Atom;
import com.radixdlt.atoms.Spin;
import com.radixdlt.crypto.ECKeyPair;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestCursors extends RadixTestWithStores {

	@Test
	public void when_deleting_an_atom_without_index__cursor_position_maintained() throws Exception {
		ECKeyPair identity = new ECKeyPair();

		// Discovery is necessary and sufficient to trigger the bug.
		// Here we use no index, as this is a separate code path.
		AtomDiscoveryRequest adr = new AtomDiscoveryRequest(Action.DISCOVER);

		executeTest(identity, adr);
	}

	@Test
	public void when_deleting_an_atom_with_index__cursor_position_maintained() throws Exception {
		ECKeyPair identity = new ECKeyPair();

		// Here we do the query with a destination index, as per client library.
		AtomDiscoveryRequest adr = new AtomDiscoveryRequest(Action.DISCOVER);
		Universe universe = Modules.get(Universe.class);
		adr.setDestination(RadixAddress.from(universe, identity.getPublicKey()).getUID());

		executeTest(identity, adr);
	}

	private void executeTest(ECKeyPair identity, AtomDiscoveryRequest adr) throws Exception {
		// Sync up to the current end of the ledger.
		// Should just be genesis for integration tests.
		syncCursor(adr);

		List<Atom> atoms = createUniqueAtoms(identity, 3);

		// Store two atoms...
		storeAtom(atoms.get(0));
		storeAtom(atoms.get(1));

		// Let the atoms be actually stored and sync our cursor...
		waitForAtomStored(atoms.get(0));
		waitForAtomStored(atoms.get(1));
		syncCursor(adr);
		assertEquals("Cursor and atom clock do not match", atomClock(atoms.get(1)), adr.getCursor().getPosition());

		// Now delete the first one...
		Modules.get(AtomStore.class).deleteAtom(atoms.get(0));

		// Write the last atom
		storeAtom(atoms.get(2));
		waitForAtomStored(atoms.get(2));

		// Make sure our cursor returns it
		Modules.get(AtomStore.class).query(adr);
		assertFalse("Query returned no atoms", adr.getInventory().isEmpty());
		assertEquals("Query returned too many atoms", 1, adr.getInventory().size());
		AID firstInventory = adr.getInventory().get(0);
		assertEquals("Wrong atom returned", atoms.get(2).getAID(), firstInventory);
		assertTrue("Cursor has no next position", adr.next());
		assertEquals("Cursor and atom clock do not match", atomClock(atoms.get(2)), adr.getCursor().getPosition());
	}

	private void waitForAtomStored(Atom atom) throws Exception {
		while (!Modules.get(AtomStore.class).hasAtom(atom.getAID())) {
			TimeUnit.MILLISECONDS.sleep(100);
		}
	}

	private long atomClock(Atom atom) {
		TemporalVertex vertex = atom.getTemporalProof().getVertexByNID(LocalSystem.getInstance().getNID());
		if (vertex == null) {
			throw new IllegalStateException("Temporal vertex not found for node "+LocalSystem.getInstance().getNID()+" in TemporalProof for Atom "+atom);
		}
		return vertex.getClock();
	}

	private void syncCursor(AtomDiscoveryRequest adr) throws Exception {
		do {
			Modules.get(AtomStore.class).query(adr);
		} while (adr.next());
	}

	private void storeAtom(Atom atom) throws Exception {
		SimpleRadixEngineAtom radixEngineAtom = RadixEngineUtils.toCMAtom(atom);
		Modules.get(AtomStore.class).storeAtom(new PreparedAtom(radixEngineAtom, UInt384.ONE));
	}

	private List<Atom> createUniqueAtoms(ECKeyPair identity, int n) throws Exception {
		Random r = new Random(); // SecureRandom not required for test
		// Super paranoid way of doing things
		Map<AID, Atom> atoms = Maps.newHashMap();
		while (atoms.size() < n) {
			Atom atom = createAtom(identity, r);
			atoms.put(atom.getAID(), atom);
		}

		// Make sure return list is ordered by atom clock.
		List<Atom> atomList = Lists.newArrayList(atoms.values());
		atomList.sort(Comparator.comparingLong(this::atomClock));
		return atomList;
	}

	private Atom createAtom(ECKeyPair identity, Random r) throws Exception {
		Atom atom = new Atom(Time.currentTimestamp());
		Universe universe = Modules.get(Universe.class);
		RadixAddress toAddress = RadixAddress.from(universe, new ECKeyPair().getPublicKey());
		RadixAddress fromAddress = RadixAddress.from(universe, identity.getPublicKey());
		MessageParticle mp = new MessageParticle(fromAddress, toAddress, Longs.toByteArray(r.nextLong()));
		atom.addParticleGroupWith(mp, Spin.UP);
		addTemporalVertex(atom);
		atom.sign(identity);
		return atom;
	}
}
