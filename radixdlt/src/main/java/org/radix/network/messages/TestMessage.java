package org.radix.network.messages;

import java.security.SecureRandom;

import org.radix.network.messaging.Message;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("network.message.test")
public final class TestMessage extends Message
{
	@JsonProperty("nonce")
	@DsonOutput(Output.ALL)
	public 	long 	testnonce;

	@JsonProperty("junk")
	@DsonOutput(Output.ALL)
	public	byte[]	junk;

	private TestMessage() {
		this(0);
		// for serializer
	}

	public TestMessage(int magic)
	{
		super(magic);
		testnonce = new SecureRandom().nextLong();
		junk = new byte[1000];
	}

	public TestMessage(final int size, int magic)
	{
		super(magic);
		testnonce = new SecureRandom().nextLong();
		junk = new byte[size];
	}

	@Override
	public String getCommand()
	{
		return "test";
	}

	public long getTestNonce ()
	{
		return testnonce;
	}

	public void setTestNonce (long testnonce)
	{
		this.testnonce = testnonce;
	}

	public byte[] getJunk()
	{
		return junk;
	}
}
