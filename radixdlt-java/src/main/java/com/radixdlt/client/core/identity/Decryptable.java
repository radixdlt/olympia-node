package com.radixdlt.client.core.identity;

import com.radixdlt.client.core.atoms.EncryptedPayload;
import java.util.function.Function;

public interface Decryptable<T> {
	EncryptedPayload getEncrypted();
	T deserialize(byte[] decrypted);
}
