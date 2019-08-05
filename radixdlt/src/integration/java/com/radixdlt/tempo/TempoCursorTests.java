package com.radixdlt.tempo;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.radix.atoms.Atom;
import org.radix.atoms.AtomStore;
import org.radix.integration.RadixTestWithStores;
import org.radix.modules.Modules;
import org.radix.modules.exceptions.ModuleException;
import org.radix.time.Time;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Longs;
import com.radixdlt.atommodel.message.MessageParticle;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atoms.Spin;
import com.radixdlt.common.AID;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerCursor.Type;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerSearchMode;
import com.radixdlt.universe.Universe;

public class TempoCursorTests extends RadixTestWithStores
{
	@Before
	public void beforeEachTest() throws ModuleException
	{
		Modules.getInstance().start(new Tempo(synchroniser, store, resolver));
	}

	@After
	public void afterEachTest() throws ModuleException
	{
		safelyStop(Modules.get(Tempo.class));

		Modules.remove(Tempo.class);
	}

	@Test
	public void store_single_atom__search_by_unique_AID_and_get() throws Exception
	{
		ECKeyPair identity = new ECKeyPair();
		
		List<Atom> atoms = createAtoms(identity, 1);
		Modules.get(Tempo.class).store(atoms.get(0));
		
		LedgerCursor cursor = Modules.get(Tempo.class).search(Type.UNIQUE, new LedgerIndex((byte) AtomStore.IDType.ATOM.ordinal(), atoms.get(0).getAID().getBytes()), LedgerSearchMode.EXACT);
		
		Assert.assertNotNull(cursor);
		Assert.assertEquals(atoms.get(0).getAID(), cursor.get());
	}
	
	@Test
	public void create_two_atoms__store_single_atom__search_by_non_existing_unique_AID__fail() throws Exception
	{
		ECKeyPair identity = new ECKeyPair();

		List<Atom> atoms = createAtoms(identity, 2);
		Modules.get(Tempo.class).store(atoms.get(0));
		
		LedgerCursor cursor = Modules.get(Tempo.class).search(Type.UNIQUE, new LedgerIndex((byte) AtomStore.IDType.ATOM.ordinal(), atoms.get(1).getAID().getBytes()), LedgerSearchMode.EXACT);
		Assert.assertNull(cursor);
	}

	@Test
	public void create_and_store_two_atoms__search_by_destination__do_get_and_next() throws Exception
	{
		ECKeyPair identity = new ECKeyPair();

		List<Atom> atoms = createAtoms(identity, 2);
		for (Atom atom : atoms)
			Modules.get(Tempo.class).store(atom);
		
		LedgerCursor cursor = Modules.get(Tempo.class).search(Type.DUPLICATE, new LedgerIndex((byte) AtomStore.IDType.DESTINATION.ordinal(), identity.getUID().toByteArray()), LedgerSearchMode.EXACT);
		Assert.assertNotNull(cursor);
		Assert.assertEquals(atoms.get(0).getAID(), cursor.get());
		
		cursor = cursor.getNext();
		Assert.assertNotNull(cursor);
		Assert.assertEquals(atoms.get(1).getAID(), cursor.get());
		
		cursor = cursor.getNext();
		Assert.assertNull(cursor);
	}

	@Test
	public void create_and_store_two_atoms__search_by_destination__get_last() throws Exception
	{
		ECKeyPair identity = new ECKeyPair();

		List<Atom> atoms = createAtoms(identity, 2);
		for (Atom atom : atoms)
			Modules.get(Tempo.class).store(atom);
		
		LedgerCursor cursor = Modules.get(Tempo.class).search(Type.DUPLICATE, new LedgerIndex((byte) AtomStore.IDType.DESTINATION.ordinal(), identity.getUID().toByteArray()), LedgerSearchMode.EXACT);
		Assert.assertNotNull(cursor);
		Assert.assertEquals(atoms.get(0).getAID(), cursor.get());
		
		cursor = cursor.getLast();
		Assert.assertNotNull(cursor);
		Assert.assertEquals(atoms.get(1).getAID(), cursor.get());
		
		cursor = cursor.getNext();
		Assert.assertNull(cursor);
	}

	@Test
	public void create_and_store_two_atoms__search_by_destination__get_next__get_first() throws Exception
	{
		ECKeyPair identity = new ECKeyPair();

		List<Atom> atoms = createAtoms(identity, 2);
		for (Atom atom : atoms)
			Modules.get(Tempo.class).store(atom);
		
		LedgerCursor cursor = Modules.get(Tempo.class).search(Type.DUPLICATE, new LedgerIndex((byte) AtomStore.IDType.DESTINATION.ordinal(), identity.getUID().toByteArray()), LedgerSearchMode.EXACT);
		Assert.assertNotNull(cursor);
		Assert.assertEquals(atoms.get(0).getAID(), cursor.get());
		
		cursor = cursor.getNext();
		Assert.assertNotNull(cursor);
		Assert.assertEquals(atoms.get(1).getAID(), cursor.get());

		cursor = cursor.getFirst();
		Assert.assertNotNull(cursor);
		Assert.assertEquals(atoms.get(0).getAID(), cursor.get());
		
		cursor = cursor.getPrev();
		Assert.assertNull(cursor);
	}

	private List<Atom> createAtoms(ECKeyPair identity, int n) throws Exception {
		Random r = new Random(); // SecureRandom not required for test
		// Super paranoid way of doing things
		Map<AID, Atom> atoms = Maps.newLinkedHashMap();
		while (atoms.size() < n) {
			Atom atom = createAtom(identity, r);
			atoms.put(atom.getAID(), atom);
		}

		// Make sure return list is ordered by atom clock.
		List<Atom> atomList = Lists.newArrayList(atoms.values());
		return atomList;
	}

	private Atom createAtom(ECKeyPair identity, Random r) throws Exception {
		Atom atom = new Atom(Time.currentTimestamp());
		Universe universe = Modules.get(Universe.class);
		RadixAddress toAddress = RadixAddress.from(universe, new ECKeyPair().getPublicKey());
		RadixAddress fromAddress = RadixAddress.from(universe, identity.getPublicKey());
		MessageParticle mp = new MessageParticle(fromAddress, toAddress, Longs.toByteArray(r.nextLong()));
		atom.addParticleGroupWith(mp, Spin.UP);
		atom.sign(identity);
		return atom;
	}

}
