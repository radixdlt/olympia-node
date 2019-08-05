package com.radixdlt.tempo;

import java.util.List;
import java.util.Map;
import java.util.Random;

import com.radixdlt.tempo.store.TempoAtomStore;
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
import com.radixdlt.universe.Universe;

import static org.mockito.Mockito.mock;

public class TempoAtomTests extends RadixTestWithStores
{
	@Before
	public void beforeEachTest() throws ModuleException
	{
		Modules.getInstance().start(Tempo.from(
			mock(AtomSynchroniser.class),
			new TempoAtomStore(() -> Modules.get(AtomStore.class)),
			mock(ConflictResolver.class)
		));	}

	@After
	public void afterEachTest() throws ModuleException
	{
		safelyStop(Modules.get(Tempo.class));

		Modules.remove(Tempo.class);
	}

	@Test
	public void store_atom() throws Exception
	{
		ECKeyPair identity = new ECKeyPair();
		
		List<Atom> atoms = createAtoms(identity, 1);
		Assert.assertTrue(Modules.get(Tempo.class).store(atoms.get(0)));
		Assert.assertEquals(atoms.get(0), Modules.get(Tempo.class).get(atoms.get(0).getAID()));
		
		// TODO should check LocalSystem clocks once implemented
	}
	
	@Test
	public void store_duplicate_atom() throws Exception
	{
		ECKeyPair identity = new ECKeyPair();
		
		List<Atom> atoms = createAtoms(identity, 1);
		Assert.assertTrue(Modules.get(Tempo.class).store(atoms.get(0)));
		Assert.assertFalse(Modules.get(Tempo.class).store(atoms.get(0)));
	}

	@Test
	public void store_atom__delete_atom__attempt_get() throws Exception
	{
		ECKeyPair identity = new ECKeyPair();

		List<Atom> atoms = createAtoms(identity, 1);
		Assert.assertTrue(Modules.get(Tempo.class).store(atoms.get(0)));
		Assert.assertEquals(atoms.get(0), Modules.get(Tempo.class).get(atoms.get(0).getAID()));

		List<Atom> deleted = Modules.get(Tempo.class).delete(atoms.get(0).getAID());
		Assert.assertEquals(1, deleted.size());
		Assert.assertTrue(deleted.contains(atoms.get(0)));

		Assert.assertNull(Modules.get(Tempo.class).get(atoms.get(0).getAID()));

		// TODO should check LocalSystem clocks once implemented
	}

	@Test
	public void store_atom__replace_atom__get_replacement__get_original() throws Exception
	{
		ECKeyPair identity = new ECKeyPair();

		List<Atom> atoms = createAtoms(identity, 2);
		Modules.get(Tempo.class).store(atoms.get(0));
		Assert.assertEquals(atoms.get(0), Modules.get(Tempo.class).get(atoms.get(0).getAID()));

		List<Atom> deleted = Modules.get(Tempo.class).replace(atoms.get(0).getAID(), atoms.get(1));
		Assert.assertEquals(1, deleted.size());
		Assert.assertTrue(deleted.contains(atoms.get(0)));

		Assert.assertNotNull(Modules.get(Tempo.class).get(atoms.get(1).getAID()));
		Assert.assertNull(Modules.get(Tempo.class).get(atoms.get(0).getAID()));
		
		// TODO should check LocalSystem clocks once implemented
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
