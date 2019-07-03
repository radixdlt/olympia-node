package org.radix.network.peers;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.radixdlt.common.EUID;
import org.radix.database.DatabaseEnvironment;
import org.radix.database.DBAction;
import org.radix.database.DatabaseStore;
import org.radix.database.exceptions.DatabaseException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.modules.exceptions.ModuleException;
import org.radix.modules.exceptions.ModuleResetException;
import org.radix.modules.exceptions.ModuleStartException;
import org.radix.network.peers.filters.PeerFilter;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import org.radix.time.Timestamps;
import com.radixdlt.utils.RadixConstants;
import org.radix.utils.SystemProfiler;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.SecondaryConfig;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.SecondaryKeyCreator;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;

public class PeerStore extends DatabaseStore
{
	private static final Logger networklog = Logging.getLogger("network");

	public static final int	MAX_CONNECTION_ATTEMPTS = 10;

	private Database 			peersDB;
	private SecondaryDatabase 	peerNIDDB;

	private class PeerNIDKeyCreator implements SecondaryKeyCreator
	{
		@Override
		public boolean createSecondaryKey(SecondaryDatabase database, DatabaseEntry key, DatabaseEntry value, DatabaseEntry secondary)
		{
			try
			{
				Peer peer = Modules.get(Serialization.class).fromDson(value.getData(), Peer.class);

				if (peer.getSystem() != null && !peer.getSystem().getNID().equals(EUID.ZERO))
				{
					secondary.setData(peer.getSystem().getNID().toByteArray());
					return true;
				}

				return false;
			}
			catch (Exception ex)
			{
				log.error("NID key failed for Peer");
				return false;
			}
		}
	}

	public PeerStore() { super(); }

	@Override
	public void start_impl() throws ModuleException
	{
		DatabaseConfig config = new DatabaseConfig();
		config.setAllowCreate(true);

		try
		{
			peersDB = Modules.get(DatabaseEnvironment.class).getEnvironment().openDatabase(null, "peers", config);

			SecondaryConfig nidConfig = new SecondaryConfig();
			nidConfig.setAllowCreate(true);
			nidConfig.setKeyCreator(new PeerNIDKeyCreator());
			nidConfig.setSortedDuplicates(false);
			peerNIDDB = Modules.get(DatabaseEnvironment.class).getEnvironment().openSecondaryDatabase(null, "peer_nids", peersDB, nidConfig);
		}
        catch (Exception ex)
        {
        	throw new ModuleStartException(ex, this);
		}

		super.start_impl();

		try (Cursor cursor = peersDB.openCursor(null, null)) {
			DatabaseEntry key = new DatabaseEntry();
			DatabaseEntry value = new DatabaseEntry();

			while (cursor.getNext(key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS)
			{
				Peer peer = Modules.get(Serialization.class).fromDson(value.getData(), Peer.class);
				peer.setTimestamp(Timestamps.CONNECTED, 0);
				byte[] bytes = Modules.get(Serialization.class).toDson(peer, Output.PERSIST);
				peersDB.put(null, key, new DatabaseEntry(bytes));
			}
		}
		catch (Exception ex)
		{
			throw new ModuleStartException(ex, this);
		}
	}

	@Override
	public void reset_impl() throws ModuleException
	{
		Transaction transaction = null;

		try
		{
			transaction = Modules.get(DatabaseEnvironment.class).getEnvironment().beginTransaction(null, new TransactionConfig().setReadUncommitted(true));
			Modules.get(DatabaseEnvironment.class).getEnvironment().truncateDatabase(transaction, "peers", false);
			Modules.get(DatabaseEnvironment.class).getEnvironment().truncateDatabase(transaction, "peer_nids", false);
			transaction.commit();
		}
		catch (DatabaseNotFoundException dsnfex)
		{
			if (transaction != null)
				transaction.abort();

			log.warn(dsnfex.getMessage());
		}
		catch (Exception ex)
		{
			if (transaction != null)
				transaction.abort();

			throw new ModuleResetException(ex, this);
		}
	}

	@Override
	public void stop_impl() throws ModuleException
	{
		super.stop_impl();

		peerNIDDB.close();
		peersDB.close();
	}

	@Override
	public void build() throws DatabaseException { /* Not used */ }

	@Override
	public void maintenence() throws DatabaseException { /* Not used */ }

	@Override
	public void integrity() throws DatabaseException { /* Not used */ }

	@Override
	public void flush() throws DatabaseException  { /* Not used */ }

	@Override
	public String getName() { return "PeerStore DB Plugin"; }

	public DBAction deletePeer(URI host) throws DatabaseException
	{
		try
        {
			DatabaseEntry key = new DatabaseEntry(host.toString().toLowerCase().getBytes(RadixConstants.STANDARD_CHARSET));

			OperationStatus status = peersDB.delete(null, key);

			if (status == OperationStatus.SUCCESS)
				networklog.debug("Deleted peer "+host);

			return new DBAction(DBAction.DELETE, true);
		}
		catch (Exception e)
		{
			throw new DatabaseException(e);
		}
	}

	public DBAction deletePeer(EUID nid) throws DatabaseException
	{
		try
        {
			DatabaseEntry key = new DatabaseEntry(nid.toByteArray());

			OperationStatus status = peerNIDDB.delete(null, key);

			if (status == OperationStatus.SUCCESS)
				networklog.debug("Deleted peer "+nid);

			return new DBAction(DBAction.DELETE, true);
		}
		catch (Exception e)
		{
			throw new DatabaseException(e);
		}
	}

	public DBAction storePeer(Peer peer) throws DatabaseException
	{
		try
        {
			if (peer.getSystem() == null)
				throw new IllegalArgumentException("Peer "+peer.getURI()+" does not have a System object");

			if (peer.getSystem().getNID().equals(EUID.ZERO))
				throw new IllegalArgumentException("Peer "+peer.getURI()+" UID is ZERO");

			// Ensure we only allow ONE instance of a NID //
			Peer existingPeer = getPeer(peer.getSystem().getNID());

			if (existingPeer != null && !existingPeer.getURI().equals(peer.getURI()))
			{
				DatabaseEntry key = new DatabaseEntry(peer.getSystem().getNID().toByteArray());

				if (peerNIDDB.delete(null, key) == OperationStatus.SUCCESS)
					networklog.debug("Removed "+existingPeer+" associated with "+peer.getSystem().getNID());
				else
					throw new DatabaseException("Peer "+peer+" storage failed");
			}

			DatabaseEntry key = new DatabaseEntry(peer.getURI().toString().toLowerCase().getBytes(RadixConstants.STANDARD_CHARSET));
			byte[] bytes = Modules.get(Serialization.class).toDson(peer, Output.PERSIST);
			DatabaseEntry value = new DatabaseEntry(bytes);

			if (peersDB.put(null, key, value) == OperationStatus.SUCCESS)
				networklog.debug("Updated "+peer);
			else
				throw new DatabaseException("Failed to store '"+peer+"'");

			return new DBAction(DBAction.STORE, peer, true);
		}
		catch (DatabaseException dbex)
		{
			throw dbex;
		}
		catch (Exception e)
		{
			throw new DatabaseException(e);
		}
	}

	public boolean hasPeer(URI host) throws DatabaseException
	{
		try
        {
			DatabaseEntry key = new DatabaseEntry(host.toString().toLowerCase().getBytes(RadixConstants.STANDARD_CHARSET));

		    if (peersDB.get(null, key, null, LockMode.DEFAULT) == OperationStatus.SUCCESS)
		    	return true;
		}
		catch (Exception e)
		{
			throw new DatabaseException(e);
		}

		return false;
	}

	public Peer getPeer(URI host) throws DatabaseException
	{
		try (Cursor cursor = peersDB.openCursor(null, null)) {
			DatabaseEntry key = new DatabaseEntry(host.toString().toLowerCase().getBytes(RadixConstants.STANDARD_CHARSET));
		    DatabaseEntry value = new DatabaseEntry();

		    while (cursor.getNext(key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS)
		    {
		    	Peer peer = Modules.get(Serialization.class).fromDson(value.getData(), Peer.class);

		    	if (peer.getURI().getHost().equalsIgnoreCase(host.getHost()) &&
		    		peer.getURI().getPort() == host.getPort())
		    		return peer;
		    }
		}
		catch (Exception e)
		{
			throw new DatabaseException(e);
		}

		return null;
	}

	public List<Peer> getPeers(int index, int limit, PeerFilter filter) throws DatabaseException
	{
		if (limit < 1)
			throw new IllegalArgumentException("Limit can not be less than 1");

		if (index < 0)
			throw new IllegalArgumentException("Index can not be less than 0");

		List<Peer>	peers = new ArrayList<>();

		long start = SystemProfiler.getInstance().begin();
		try (Cursor cursor = this.peersDB.openCursor(null, null)) {
			OperationStatus status = OperationStatus.SUCCESS;
		    DatabaseEntry key = new DatabaseEntry();
		    DatabaseEntry data = new DatabaseEntry();

	    	status = cursor.getFirst(key, data, LockMode.DEFAULT);

	    	if (index > 0)
	    	{
	    		long skipped = cursor.skipNext(index, key, data, LockMode.DEFAULT);

	    		if (skipped != index)
	    			status = OperationStatus.NOTFOUND;
	    	}

		    while (status == OperationStatus.SUCCESS)
		    {
		    	Peer peer = Modules.get(Serialization.class).fromDson(data.getData(), Peer.class);

		    	if (filter == null || !filter.filter(peer))
		    		peers.add(peer);

		    	if (peers.size() == limit)
		    		break;

		    	status = cursor.getNext(key, data, LockMode.DEFAULT);
		    }
		}
		catch (Exception e)
		{
			throw new DatabaseException(e);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("PEER_STORE:GET_RANDOM_FILTERED_PEERS", start);
		}

		return peers;
	}

	public List<Peer> getPeers(PeerFilter filter) throws DatabaseException
	{
		List<Peer>	peers = new ArrayList<>();

		long start = SystemProfiler.getInstance().begin();

		try (Cursor cursor = peersDB.openCursor(null, null)) {
		    DatabaseEntry key = new DatabaseEntry();
		    DatabaseEntry value = new DatabaseEntry();

		    while (cursor.getNext(key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS)
		    {
		    	Peer peer = Modules.get(Serialization.class).fromDson(value.getData(), Peer.class);

		    	if (filter == null || !filter.filter(peer)) {
		    		peers.add(peer);
		    	}
		    }
		}
		catch (Exception e)
		{
			throw new DatabaseException(e);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("PEER_STORE:GET_FILTERED_PEERS", start);
		}

		return peers;
	}

	public boolean hasPeer(EUID nid) throws DatabaseException
	{
		try
        {
			DatabaseEntry search = new DatabaseEntry(nid.toByteArray());
			DatabaseEntry key = new DatabaseEntry();

			if (peerNIDDB.get(null, search, key, null, LockMode.DEFAULT) == OperationStatus.SUCCESS)
		    	return true;
		}
		catch (Exception e)
		{
			throw new DatabaseException(e);
		}

		return false;
	}

	public Peer getPeer(EUID nid) throws DatabaseException
	{
		try
        {
			DatabaseEntry search = new DatabaseEntry(nid.toByteArray());
			DatabaseEntry key = new DatabaseEntry();
			DatabaseEntry value = new DatabaseEntry();

			if (peerNIDDB.get(null, search, key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS)
				return Modules.get(Serialization.class).fromDson(value.getData(), Peer.class);
		}
		catch (Exception e)
		{
			throw new DatabaseException(e);
		}

		return null;
	}
}

