package org.radix.atoms;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import com.radixdlt.common.EUID;
import com.radixdlt.utils.WireIO;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import org.radix.utils.Bloomer;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("serial")
@SerializerId2("store.atom_store_bloom")
public class AtomStoreBloom extends Bloomer<EUID>
{
	@Override
	public short VERSION() { return 100; }

	@JsonProperty("clock")
	@DsonOutput(Output.ALL)
	private long clock;

	AtomStoreBloom()
	{
		super();
	}

	public AtomStoreBloom(double falsePositiveProbability, int expectedNumberOfElements, String label)
	{
	    super(falsePositiveProbability, expectedNumberOfElements, label);

	    clock = 0;
	}

	public AtomStoreBloom(InputStream inputStream) throws IOException
	{
		super(inputStream);
	}

	public void add(EUID element, long clock)
	{
		synchronized(this)
		{
			super.add(element);

			this.clock = clock;
		}
	}

	public void add(byte[] bytes, long clock)
	{
		synchronized(this)
		{
			super.add(bytes);

			this.clock = clock;
		}
	}

	public long getClock()
	{
		return this.clock;
	}

	@Override
	public void clear()
	{
		super.clear();

		this.clock = 0;
	}

	@Override
	public void add(EUID element)
	{
		throw new UnsupportedOperationException("Not supported by AtomStoreBloom");
	}

	@Override
	public void add(byte[] bytes)
	{
		throw new UnsupportedOperationException("Not supported by AtomStoreBloom");
	}

	@Override
	public void addAll(Collection<? extends EUID> c)
	{
		throw new UnsupportedOperationException("Not supported by AtomStoreBloom");
	}

	// SERIALIZER //
	@Override
	public void serialize(WireIO.Writer writer) throws IOException
	{
		super.serialize(writer);

		writer.writeLong(clock);
	}

	@Override
	public void deserialize(WireIO.Reader reader) throws IOException
	{
		super.deserialize(reader);

		this.clock = reader.readLong();
	}
}
