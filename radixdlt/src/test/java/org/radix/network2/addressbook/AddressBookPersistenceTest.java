package org.radix.network2.addressbook;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;
import org.radix.database.DatabaseEnvironment;
import org.radix.modules.Modules;
import org.radix.modules.exceptions.ModuleException;
import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.TransportInfo;
import org.radix.serialization.RadixTest;
import org.radix.time.Time;
import org.radix.time.Timestamps;

import com.radixdlt.common.EUID;
import com.radixdlt.serialization.Serialization;

import static org.junit.Assert.*;

public class AddressBookPersistenceTest extends RadixTest {

	private AddressBookPersistence abp;

	@Before
	public void setUp() throws Exception {
		Modules.getInstance().start(new DatabaseEnvironment());
		this.abp = new AddressBookPersistence(Serialization.getDefault());
		this.abp.reset_impl();
	}

	@After
	public void tearDown() throws Exception {
		this.abp.stop_impl();
		Modules.getInstance().stop(Modules.get(DatabaseEnvironment.class));
		Modules.remove(DatabaseEnvironment.class);
	}

	@Test
	public void testStart() throws ModuleException {
		// No exceptions, and should have a database when done
		this.abp.start_impl();
		assertNotNull(Whitebox.getInternalState(this.abp, "peersByNidDB"));
	}

	@Test
	public void testStop() throws ModuleException {
		// No exceptions, and should have no database when done
		this.abp.start_impl();
		assertNotNull(Whitebox.getInternalState(this.abp, "peersByNidDB"));
		this.abp.stop_impl();
		assertNull(Whitebox.getInternalState(this.abp, "peersByNidDB"));
	}

	@Test
	public void testReset() throws ModuleException {
		this.abp.start_impl();
		assertTrue(this.abp.savePeer(new PeerWithNid(EUID.ONE)));
		AtomicInteger peercount1 = new AtomicInteger(0);
		this.abp.forEachPersistedPeer(p -> peercount1.incrementAndGet());
		assertEquals(1, peercount1.get());

		this.abp.stop_impl();
		this.abp.reset_impl();

		this.abp.start_impl();
		AtomicInteger peercount2 = new AtomicInteger(0);
		this.abp.forEachPersistedPeer(p -> peercount2.incrementAndGet());
		assertEquals(0, peercount2.get());
	}

	@Test
	public void testGetName() {
		assertEquals("Peer Address Book Persistence", this.abp.getName());
	}

	@Test
	public void testBuild() {
		// No exceptions
		this.abp.build();
	}

	@Test
	public void testMaintenence() {
		// No exceptions
		this.abp.maintenence();
	}

	@Test
	public void testIntegrity() {
		// No exceptions
		this.abp.integrity();
	}

	@Test
	public void testFlush() {
		// No exceptions
		this.abp.flush();
	}

	@Test
	public void testSavePeer() throws ModuleException {
		this.abp.start_impl();

		PeerWithNid pwn = new PeerWithNid(EUID.ONE);
		assertTrue(this.abp.savePeer(pwn));
		assertEquals(1, peerCount());
		assertEquals(0L, onlyPeer().getTimestamp(Timestamps.ACTIVE));

		// Update timestamp
		long now = Time.currentTimestamp();
		pwn.setTimestamp(Timestamps.ACTIVE, now);
		assertTrue(this.abp.savePeer(pwn));
		assertEquals(1, peerCount());
		assertEquals(now, onlyPeer().getTimestamp(Timestamps.ACTIVE));

		// Add new peer
		PeerWithNid pwn2 = new PeerWithNid(EUID.TWO);
		assertTrue(this.abp.savePeer(pwn2));
		assertEquals(2, peerCount());

		// Can't save peer without a nid
		TransportInfo ti = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());
		PeerWithTransport pwt = new PeerWithTransport(ti);
		assertFalse(this.abp.savePeer(pwt));
	}

	@Test
	public void testDeletePeer() throws ModuleException {
		this.abp.start_impl();

		PeerWithNid pwn1 = new PeerWithNid(EUID.ONE);
		assertTrue(this.abp.savePeer(pwn1));
		assertEquals(1, peerCount());
		PeerWithNid pwn2 = new PeerWithNid(EUID.TWO);
		assertTrue(this.abp.savePeer(pwn2));
		assertEquals(2, peerCount());

		// Delete one, and check that the only one left is the right one
		assertTrue(this.abp.deletePeer(EUID.ONE));
		assertEquals(EUID.TWO, onlyPeer().getNID());

		// Add back the deleted one, and delete the other
		assertTrue(this.abp.savePeer(pwn1));
		assertEquals(2, peerCount());
		assertTrue(this.abp.deletePeer(EUID.TWO));
		assertEquals(EUID.ONE, onlyPeer().getNID());

		// Try to delete something that doesn't exist
		assertFalse(this.abp.deletePeer(EUID.TWO));
	}

	private int peerCount() {
		AtomicInteger peercount = new AtomicInteger(0);
		this.abp.forEachPersistedPeer(p -> peercount.incrementAndGet());
		return peercount.get();
	}

	private Peer onlyPeer() {
		AtomicReference<Peer> peer = new AtomicReference<>();
		this.abp.forEachPersistedPeer(p -> assertTrue(peer.compareAndSet(null, p)));
		assertNotNull(peer.get());
		return peer.get();
	}
}
