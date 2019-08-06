package com.radixdlt.tempo;

import java.util.List;
import java.util.Map;
import java.util.Random;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.Atom;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.tempo.store.TempoAtomStore;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.radix.atoms.AtomStore;
import org.radix.atoms.sync.AtomSync;
import org.radix.database.DatabaseEnvironment;
import org.radix.integration.RadixTestWithStores;
import org.radix.modules.Modules;
import org.radix.modules.exceptions.ModuleException;
import org.radix.routing.RoutingHandler;
import org.radix.routing.RoutingStore;
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
		Modules.getInstance().start(clean(new TempoAtomStore()));

		Modules.getInstance().start(Tempo.from(
			mock(AtomSynchroniser.class),
//			new WrappedLegacyTempoAtomStore(() -> Modules.get(AtomStore.class)),
			Modules.get(TempoAtomStore.class),
			mock(ConflictResolver.class)
		));
	}

	@After
	public void afterEachTest() throws ModuleException
	{
		safelyStop(Modules.get(Tempo.class));
		safelyStop(Modules.get(TempoAtomStore.class));

		Modules.remove(TempoAtomStore.class);
		Modules.remove(Tempo.class);
	}

	@Test
	public void store_atom() throws Exception
	{
		ECKeyPair identity = new ECKeyPair();
		
		List<Atom> atoms = createAtoms(identity, 1);
		Assert.assertTrue(Modules.get(Tempo.class).store(atoms.get(0), ImmutableSet.of(), ImmutableSet.of()));
		Atom actual = Modules.get(Tempo.class).get(atoms.get(0).getAID()).get();
		Assert.assertEquals(atoms.get(0), actual);
		
		// TODO should check LocalSystem clocks once implemented
	}
	
	@Test
	public void store_duplicate_atom() throws Exception
	{
		ECKeyPair identity = new ECKeyPair();
		
		List<Atom> atoms = createAtoms(identity, 1);
		Assert.assertTrue(Modules.get(Tempo.class).store(atoms.get(0), ImmutableSet.of(), ImmutableSet.of()));
		Assert.assertFalse(Modules.get(Tempo.class).store(atoms.get(0), ImmutableSet.of(), ImmutableSet.of()));
	}

	@Test
	public void store_atom__delete_atom__attempt_get() throws Exception
	{
		ECKeyPair identity = new ECKeyPair();

		List<Atom> atoms = createAtoms(identity, 1);
		Assert.assertTrue(Modules.get(Tempo.class).store(atoms.get(0), ImmutableSet.of(), ImmutableSet.of()));
		Assert.assertEquals(atoms.get(0), Modules.get(Tempo.class).get(atoms.get(0).getAID()).get());

		boolean deleted = Modules.get(Tempo.class).delete(atoms.get(0).getAID());
		Assert.assertTrue(deleted);

		Assert.assertFalse("Deleted atom is no longer present", Modules.get(Tempo.class).get(atoms.get(0).getAID()).isPresent());

		// TODO should check LocalSystem clocks once implemented
	}

	@Test
	public void store_atom__replace_atom__get_replacement__get_original() throws Exception
	{
		ECKeyPair identity = new ECKeyPair();

		List<Atom> atoms = createAtoms(identity, 2);
		Assert.assertTrue(Modules.get(Tempo.class).store(atoms.get(0), ImmutableSet.of(), ImmutableSet.of()));
		Assert.assertEquals(atoms.get(0), Modules.get(Tempo.class).get(atoms.get(0).getAID()).get());

		boolean deleted = Modules.get(Tempo.class).replace(ImmutableSet.of(atoms.get(0).getAID()), atoms.get(1), ImmutableSet.of(), ImmutableSet.of());
		Assert.assertTrue(deleted);

		Assert.assertTrue("New atom is present", Modules.get(Tempo.class).get(atoms.get(1).getAID()).isPresent());
		Assert.assertFalse("Replaced atom is no longer present", Modules.get(Tempo.class).get(atoms.get(0).getAID()).isPresent());
		
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

	private TempoAtom createAtom(ECKeyPair identity, Random r) throws Exception {
		Universe universe = Modules.get(Universe.class);
		RadixAddress toAddress = RadixAddress.from(universe, new ECKeyPair().getPublicKey());
		RadixAddress fromAddress = RadixAddress.from(universe, identity.getPublicKey());
		MessageParticle mp = new MessageParticle(fromAddress, toAddress, Longs.toByteArray(r.nextLong()));

		ImmutableAtom content = new ImmutableAtom(Time.currentTimestamp(), ImmutableMap.of());
		content.addParticleGroupWith(mp, Spin.UP);
		content.sign(identity);

		TempoAtom atom = new TempoAtom(content, content.getAID(), content.getTimestamp(), content.getShards());
		return atom;
	}
}
