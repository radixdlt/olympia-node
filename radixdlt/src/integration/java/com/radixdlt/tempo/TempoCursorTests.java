package com.radixdlt.tempo;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.Atom;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerCursor.Type;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerSearchMode;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.radix.atoms.AtomStore;
import org.radix.integration.RadixTestWithStores;
import org.radix.modules.Modules;

import java.util.List;

import static org.junit.Assume.assumeTrue;

public class TempoCursorTests extends RadixTestWithStores {

	private AtomGenerator atomGenerator = new AtomGenerator();

	@BeforeClass
	public static void checkTempoAvailable() {
		assumeTrue("Tempo 2.0 must be available", Modules.isAvailable(Tempo.class));
	}

	@Test
	public void store_single_atom__search_by_unique_aid_and_get() throws Exception {
		ECKeyPair identity = new ECKeyPair();

		List<Atom> atoms = atomGenerator.createAtoms(identity, 1);
		Assert.assertTrue(Modules.get(Tempo.class).store(atoms.get(0), ImmutableSet.of(), ImmutableSet.of()));

		LedgerCursor cursor = Modules.get(Tempo.class).search(Type.UNIQUE, new LedgerIndex((byte) AtomStore.IDType.ATOM.ordinal(), atoms.get(0).getAID().getBytes()), LedgerSearchMode.EXACT);

		Assert.assertNotNull(cursor);
		Assert.assertEquals(atoms.get(0).getAID(), cursor.get());
	}

	@Test
	public void create_two_atoms__store_single_atom__search_by_non_existing_unique_aid__fail() throws Exception {
		List<Atom> atoms = atomGenerator.createAtoms(2);
		Assert.assertTrue(Modules.get(Tempo.class).store(atoms.get(0), ImmutableSet.of(), ImmutableSet.of()));

		LedgerCursor cursor = Modules.get(Tempo.class).search(Type.UNIQUE, new LedgerIndex((byte) AtomStore.IDType.ATOM.ordinal(), atoms.get(1).getAID().getBytes()), LedgerSearchMode.EXACT);
		Assert.assertNull(cursor);
	}

	@Test
	public void create_and_store_two_atoms__search_by_index__do_get_and_next() throws Exception {
		ECKeyPair identity = new ECKeyPair();

		LedgerIndex index = new LedgerIndex((byte) AtomStore.IDType.DESTINATION.ordinal(), identity.getUID().toByteArray());
		List<Atom> atoms = atomGenerator.createAtoms(identity, 2);
		for (Atom atom : atoms) {
			Assert.assertTrue(Modules.get(Tempo.class).store(atom, ImmutableSet.of(), ImmutableSet.of(index)));
		}

		LedgerCursor cursor = Modules.get(Tempo.class).search(Type.DUPLICATE, index, LedgerSearchMode.EXACT);
		Assert.assertNotNull(cursor);
		Assert.assertEquals(atoms.get(0).getAID(), cursor.get());

		cursor = cursor.next();
		Assert.assertNotNull(cursor);
		Assert.assertEquals(atoms.get(1).getAID(), cursor.get());

		cursor = cursor.next();
		Assert.assertNull(cursor);
	}

	@Test
	public void create_and_store_two_atoms__search_by_index__get_last() throws Exception {
		ECKeyPair identity = new ECKeyPair();

		LedgerIndex index = new LedgerIndex((byte) AtomStore.IDType.DESTINATION.ordinal(), identity.getUID().toByteArray());
		List<Atom> atoms = atomGenerator.createAtoms(identity, 2);
		for (Atom atom : atoms) {
			Assert.assertTrue(Modules.get(Tempo.class).store(atom, ImmutableSet.of(), ImmutableSet.of(index)));
		}

		LedgerCursor cursor = Modules.get(Tempo.class).search(Type.DUPLICATE, index, LedgerSearchMode.EXACT);
		Assert.assertNotNull(cursor);
		Assert.assertEquals(atoms.get(0).getAID(), cursor.get());

		cursor = cursor.last();
		Assert.assertNotNull(cursor);
		Assert.assertEquals(atoms.get(1).getAID(), cursor.get());

		cursor = cursor.next();
		Assert.assertNull(cursor);
	}

	@Test
	public void create_and_store_two_atoms__search_by_index__get_next__get_first() throws Exception {
		ECKeyPair identity = new ECKeyPair();

		LedgerIndex index = new LedgerIndex((byte) AtomStore.IDType.DESTINATION.ordinal(), identity.getUID().toByteArray());
		List<Atom> atoms = atomGenerator.createAtoms(identity, 2);
		for (Atom atom : atoms) {
			Assert.assertTrue(Modules.get(Tempo.class).store(atom, ImmutableSet.of(), ImmutableSet.of(index)));
		}

		LedgerCursor cursor = Modules.get(Tempo.class).search(Type.DUPLICATE, index, LedgerSearchMode.EXACT);
		Assert.assertNotNull(cursor);
		Assert.assertEquals(atoms.get(0).getAID(), cursor.get());

		cursor = cursor.next();
		Assert.assertNotNull(cursor);
		Assert.assertEquals(atoms.get(1).getAID(), cursor.get());

		cursor = cursor.first();
		Assert.assertNotNull(cursor);
		Assert.assertEquals(atoms.get(0).getAID(), cursor.get());

		cursor = cursor.previous();
		Assert.assertNull(cursor);
	}
}
