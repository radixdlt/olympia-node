package org.radix.containers;

import com.radixdlt.common.EUID;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import org.radix.crypto.Hashable;
import org.radix.interfaces.Versioned;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;

import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class BasicContainer implements Hashable, Versioned, Cloneable, Comparable<Object>
{
	private static final Logger log = Logging.getLogger ();

	@JsonProperty("version")
	@DsonOutput(Output.ALL)
	private short	version;

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	private	Hash	hash = Hash.ZERO_HASH;

	public BasicContainer()
	{
		super();

		version = VERSION();
	}

	/**
	 * Copy constructor.
	 * @param copy {@link BasicContainer} to copy values from.
	 */
	public BasicContainer(BasicContainer copy) {
		this();
		this.version = copy.VERSION();
		this.hash = copy.hash;
	}

	@Override
	public boolean equals(Object o)
	{
		if (o == null) return false;
		if (o == this) return true;

		if (getClass().isInstance(o) && getHash().equals(((BasicContainer)o).getHash()))
				return true;

		return super.equals(o);
	}

	@Override
	public int hashCode()
	{
		return getHash().hashCode();
	}

	// HASHABLE //
	@Override
	public synchronized Hash getHash()
	{
		try
		{
			if (hash == null || hash.equals(Hash.ZERO_HASH)) {
				byte[] hashBytes = Modules.get(Serialization.class).toDson(this, Output.HASH);
				hash = new Hash(Hash.hash256(hashBytes));
			}

			return hash;
		}
		catch (Exception e)
		{
			throw new RuntimeException("Error generating hash: " + e, e);
		}
	}

	// HID //
	@JsonProperty("hid")
	@DsonOutput(Output.API)
	public synchronized final EUID getHID()
	{
		return getHash().getID();
	}

	/**
	 * Resets all deterministic content of this object.
	 * <br><br>
	 * Hashes, states, signatures and similar should be reset in subclasses.
	 * @param accessor Currently ignored.
	 */
	public void reset(ECKeyPair accessor)
	{
		this.hash = Hash.ZERO_HASH;
	}

	@Override
	public String toString()
	{
		return this.getClass().toString()+": "+getHID().toString();
	}

	@Override
	public int compareTo(Object object)
	{
		if (object instanceof BasicContainer)
			return getHID().compareTo(((BasicContainer)object).getHID());

		return 0;
	}

	@Override
	public short getVersion()
	{
		return version;
	}

	@Override
	public void setVersion(short version)
	{
		this.version = version;
	}
}
