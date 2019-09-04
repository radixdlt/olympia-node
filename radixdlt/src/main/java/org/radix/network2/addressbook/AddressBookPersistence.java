package org.radix.network2.addressbook;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.function.Consumer;
import org.radix.database.DatabaseEnvironment;
import org.radix.database.DatabaseStore;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.modules.exceptions.ModuleException;
import org.radix.modules.exceptions.ModuleResetException;
import org.radix.modules.exceptions.ModuleStartException;

import com.radixdlt.common.EUID;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;

/**
 * Persistence for peers.
 */
public class AddressBookPersistence extends DatabaseStore implements PeerPersistence {
	private static final Logger log = Logging.getLogger("addressbook");

	private final Serialization serialization;
	private Database peersByNidDB;

	AddressBookPersistence(Serialization serialization) {
		super();
		this.serialization = Objects.requireNonNull(serialization);
	}

	@Override
	public void start_impl() throws ModuleException {
		DatabaseConfig config = new DatabaseConfig();
		config.setAllowCreate(true);

		try {
			this.peersByNidDB = Modules.get(DatabaseEnvironment.class).getEnvironment().openDatabase(null, "peers_by_nid", config);
		} catch (DatabaseException | IllegalArgumentException | IllegalStateException ex) {
        	throw new ModuleStartException(ex, this);
		}

		super.start_impl();
	}

	@Override
	public void reset_impl() throws ModuleException {
		Transaction transaction = null;

		try {
			transaction = Modules.get(DatabaseEnvironment.class).getEnvironment().beginTransaction(null, new TransactionConfig().setReadUncommitted(true));
			Modules.get(DatabaseEnvironment.class).getEnvironment().truncateDatabase(transaction, "peers_by_nid", false);
			transaction.commit();
		} catch (DatabaseNotFoundException dsnfex) {
			if (transaction != null) {
				transaction.abort();
			}
			log.warn(dsnfex.getMessage());
		} catch (Exception ex) {
			if (transaction != null) {
				transaction.abort();
			}
			throw new ModuleResetException(ex, this);
		}
	}

	@Override
	public void stop_impl() throws ModuleException {
		super.stop_impl();
		this.peersByNidDB.close();
	}

	@Override
	public void build() throws DatabaseException {
		// Not used
	}

	@Override
	public void maintenence() throws DatabaseException {
		// Not used
	}

	@Override
	public void integrity() throws DatabaseException {
		// Not used
	}

	@Override
	public void flush() throws DatabaseException  {
		// Not used
	}

	@Override
	public String getName() {
		return "Peer Address Book Persistence";
	}

	@Override
	public boolean savePeer(Peer peer) {
		if (peer.hasNID()) {
			try {
				DatabaseEntry key = new DatabaseEntry(peer.getNID().toByteArray());
				byte[] bytes = serialization.toDson(peer, Output.PERSIST);
				DatabaseEntry value = new DatabaseEntry(bytes);
				return (peersByNidDB.put(null, key, value) == OperationStatus.SUCCESS);
			} catch (SerializationException e) {
				log.error("Failure updating " + peer);
			}
		}
		return false;
	}

	@Override
	public boolean deletePeer(EUID nid) {
		DatabaseEntry key = new DatabaseEntry(nid.toByteArray());
		return (peersByNidDB.delete(null, key) == OperationStatus.SUCCESS);
	}

	@Override
	public void forEachPersistedPeer(Consumer<Peer> c) {
		try (Cursor cursor = this.peersByNidDB.openCursor(null, null)) {
			DatabaseEntry key = new DatabaseEntry();
			DatabaseEntry value = new DatabaseEntry();

			while (cursor.getNext(key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				Peer peer = this.serialization.fromDson(value.getData(), Peer.class);
				c.accept(peer);
			}
		} catch (IOException ex) {
			throw new UncheckedIOException("Error while loading database", ex);
		}
	}
}

