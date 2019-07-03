package com.radixdlt.atommodel.unique;

import org.junit.BeforeClass;
import org.junit.Test;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.atomos.test.TestAtomOS;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UniqueParticleConstraintScryptTest {
	private static TestAtomOS testAtomModelOS = new TestAtomOS();

	@BeforeClass
	public static void initializeConstraintScrypt() {
		UniqueParticleConstraintScrypt messageParticleConstraintScrypt = new UniqueParticleConstraintScrypt();
		messageParticleConstraintScrypt.main(testAtomModelOS);
	}

	@Test
	public void when_validating_message_with_signed_from__result_has_no_error() {
		UniqueParticle message = mock(UniqueParticle.class);
		RadixAddress from = mock(RadixAddress.class);
		when(message.getAddress()).thenReturn(from);
		AtomMetadata metadata = mock(AtomMetadata.class);
		when(metadata.isSignedBy(from)).thenReturn(true);

		testAtomModelOS.testInitialParticle(message, metadata)
			.assertNoErrorWithMessageContaining("sign");
	}

	@Test
	public void when_validating_message_with_unsigned_from__result_has_error() {
		UniqueParticle message = mock(UniqueParticle.class);
		RadixAddress from = mock(RadixAddress.class);
		when(message.getAddress()).thenReturn(from);
		AtomMetadata metadata = mock(AtomMetadata.class);
		when(metadata.isSignedBy(from)).thenReturn(false);

		testAtomModelOS.testInitialParticle(message, metadata)
			.assertErrorWithMessageContaining("sign");
	}
}