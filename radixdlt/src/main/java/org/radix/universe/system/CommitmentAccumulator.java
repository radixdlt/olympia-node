package org.radix.universe.system;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.crypto.Hash;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import org.radix.crypto.Hashable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.StampedLock;

@SerializerId2("internal.dbs_accumulator")
public class CommitmentAccumulator implements Hashable
{
	private final StampedLock lock = new StampedLock();

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("length")
	@DsonOutput(Output.ALL)
	private int 	length;

	@JsonProperty("modifications")
	@DsonOutput(Output.ALL)
	private long 	modifications;

	private byte[][] elements;

	private CommitmentAccumulator()
	{
		// Required for serializer
	}

	CommitmentAccumulator(int length)
	{
		if (length % 8 != 0) {
			throw new IllegalArgumentException("Length must be a multiple of 8: " + length);
		}

		this.modifications = 0;
		this.length = length;
		this.elements = new byte[length][length / 8];
	}

	public int length()
	{
		return this.length;
	}

	public long modifications()
	{
		return this.modifications;
	}

	public boolean has(AID aid)
	{
		byte[] aidBytes = aid.getBytes();

		long stamp = lock.readLock();
		try {
			for (int i = 0 ; i < length ; i++)
			{
				if (Arrays.equals(elements[i], aidBytes))
					return true;
			}
		} finally {
			lock.unlockRead(stamp);
		}

		return false;
	}

	public void put(AID aid)
	{
		final long stamp = lock.writeLock();
		try {
			java.lang.System.arraycopy(this.elements, 0, this.elements, 1, length - 1);
			this.elements[0] = aid.getBytes();
			this.modifications++;
		} finally {
			lock.unlockWrite(stamp);
		}
	}

	@Override
	public Hash getHash()
	{
		return new Hash(toByteArray());
	}

	public byte[] toByteArray()
	{
		byte[] bytes = new byte[this.length / 8];

		final long stamp = lock.readLock();
		try {
			for (int i = 0; i < this.length; i++) {
				int elementIndex = i >>> 3;
				int bitIndex = i & 7;
				bytes[elementIndex] |= (this.elements[i][elementIndex] & 0xff) & (1 << bitIndex);
			}
		} finally {
			lock.unlockRead(stamp);
		}

		return bytes;
	}

	// Property Signatures: 1 getter, 1 setter
	@JsonProperty("elements")
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	private List<byte[]> getJsonElements() {
		return ImmutableList.copyOf(this.elements);
	}

	@JsonProperty("elements")
	private void setJsonElements(Collection<byte[]> elements) {
		if (elements != null) {
			this.elements = elements.toArray(new byte[elements.size()][]);
		}
	}
}
