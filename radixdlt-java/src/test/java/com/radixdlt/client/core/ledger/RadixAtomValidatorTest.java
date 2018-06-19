package com.radixdlt.client.core.ledger;

import static org.junit.Assert.*;

import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.ApplicationPayloadAtom;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.atoms.AtomValidationException;
import com.radixdlt.client.core.atoms.Consumable;
import com.radixdlt.client.core.atoms.Consumer;
import com.radixdlt.client.core.atoms.TransactionAtom;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.identity.OneTimeUseIdentity;
import com.radixdlt.client.core.identity.RadixIdentity;
import java.math.BigInteger;
import org.junit.Test;

public class RadixAtomValidatorTest {

	@Test(expected = AtomValidationException.class)
	public void testSignatureValidation() throws AtomValidationException {
		RadixIdentity radixIdentity = new OneTimeUseIdentity();
		UnsignedAtom unsignedAtom = new AtomBuilder()
			.type(TransactionAtom.class)
			.addParticle(new Consumer(100000000000L, radixIdentity.getPublicKey().toECKeyPair(), System.nanoTime(), Asset.XRD.getId()))
			.addParticle(new Consumable(100000000000L, radixIdentity.getPublicKey().toECKeyPair(), System.nanoTime(), Asset.XRD.getId()))
			.build();

		RadixAtomValidator validator = RadixAtomValidator.getInstance();
		validator.validateSignatures(unsignedAtom.getRawAtom());
	}

	@Test
	public void testPayloadValidationWithNoSignatures() throws AtomValidationException {
		UnsignedAtom unsignedAtom = new AtomBuilder()
			.type(ApplicationPayloadAtom.class)
			.applicationId("Test")
			.payload("Hello")
			.addDestination(new EUID(BigInteger.valueOf(1L)))
			.build();

		RadixAtomValidator validator = RadixAtomValidator.getInstance();
		validator.validateSignatures(unsignedAtom.getRawAtom());
	}
}