/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package org.radix.containers;

import com.radixdlt.common.EUID;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;

import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class BasicContainer
{
	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	private	Hash	hash = Hash.ZERO_HASH;

	public BasicContainer()
	{
		super();
	}

	/**
	 * Copy constructor.
	 * @param copy {@link BasicContainer} to copy values from.
	 */
	public BasicContainer(BasicContainer copy) {
		this();
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
	public synchronized Hash getHash()
	{
		try
		{
			if (hash == null || hash.equals(Hash.ZERO_HASH)) {
				byte[] hashBytes = Serialization.getDefault().toDson(this, Output.HASH);
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

	public abstract short VERSION();
}
