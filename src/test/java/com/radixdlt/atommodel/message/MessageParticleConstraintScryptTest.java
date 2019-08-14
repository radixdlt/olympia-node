package com.radixdlt.atommodel.message;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atomos.test.TestAtomOS;
import com.radixdlt.constraintmachine.AtomMetadata;
import org.junit.BeforeClass;
import org.junit.Test;

public class MessageParticleConstraintScryptTest {
	private static TestAtomOS testAtomModelOS = new TestAtomOS();

	@BeforeClass
	public static void initializeConstraintScrypt() {
		MessageParticleConstraintScrypt messageParticleConstraintScrypt = new MessageParticleConstraintScrypt();
		messageParticleConstraintScrypt.main(testAtomModelOS);
	}

	@Test
	public void when_validating_message_with_signed_from__result_has_no_error() {
		MessageParticle message = mock(MessageParticle.class);
		RadixAddress from = mock(RadixAddress.class);
		when(message.getFrom()).thenReturn(from);
		AtomMetadata metadata = mock(AtomMetadata.class);
		when(metadata.isSignedBy(from)).thenReturn(true);

		testAtomModelOS.testInitialParticle(message, metadata)
			.assertNoErrorWithMessageContaining("signed");
	}
}