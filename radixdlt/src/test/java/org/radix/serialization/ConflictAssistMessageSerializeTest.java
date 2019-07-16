package org.radix.serialization;

import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.atommodel.message.MessageParticle;
import com.radixdlt.universe.Universe;
import org.radix.atoms.particles.conflict.messages.ConflictAssistRequestMessage;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.utils.RadixConstants;
import org.radix.modules.Modules;

/**
 * Check serialization of ConflictAssistMessage
 */
public class ConflictAssistMessageSerializeTest extends SerializeMessageObject<ConflictAssistRequestMessage> {
	public ConflictAssistMessageSerializeTest() {
		super(ConflictAssistRequestMessage.class, ConflictAssistMessageSerializeTest::get);
	}

	private static ConflictAssistRequestMessage get() {
		try {
			Universe universe = Modules.get(Universe.class);
			ECPublicKey key = new ECKeyPair().getPublicKey();
			SpunParticle p = SpunParticle.up(
				new MessageParticle(RadixAddress.from(universe, key), RadixAddress.from(universe, key), "This is some test data".getBytes(RadixConstants.STANDARD_CHARSET))
			);
			return new ConflictAssistRequestMessage(p);
		} catch (CryptoException e) {
			throw new IllegalStateException("Can't create ConflictAssistMessage", e);
		}
	}
}
