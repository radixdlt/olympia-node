package org.radix.network.messaging;

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.ECSignature;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;

import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class SignedMessage extends Message
{
	@JsonProperty("key")
	@DsonOutput(Output.ALL)
	private ECKeyPair 	key;

	@JsonProperty("signature")
	@DsonOutput(Output.ALL)
	private ECSignature signature;

	@Override
	public short VERSION() { return 100; }

	protected SignedMessage()
	{
		super();
	}

	// SIGNABLE //
	public final ECSignature getSignature()
	{
		return this.signature;
	}

	public final void setSignature(ECSignature signature)
	{
		this.signature = signature;
	}

	public void sign(ECKeyPair key) throws CryptoException
	{
		this.key = key;
		this.signature = key.sign(getHash());
	}

	public boolean verify(ECPublicKey key) {
		return key.verify(getHash(), this.signature);
	}
}
