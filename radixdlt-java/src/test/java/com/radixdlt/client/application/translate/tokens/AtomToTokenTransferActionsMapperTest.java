package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.client.core.atoms.particles.SpunParticle;

import java.util.Map;
import org.junit.Test;

import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.observers.TestObserver;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.utils.UInt256;

public class AtomToTokenTransferActionsMapperTest {
	@Test
	public void testSendToSelfTest() {
		RadixAddress myAddress = mock(RadixAddress.class);
		when(myAddress.getUID()).thenReturn(EUID.ONE);
		RRI tokenDefinitionReference = mock(RRI.class);
		when(tokenDefinitionReference.getName()).thenReturn("JOSH");
		when(tokenDefinitionReference.getAddress()).thenReturn(RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor"));

		TransferrableTokensParticle ttp = new TransferrableTokensParticle(
			UInt256.ONE, UInt256.ONE, myAddress, 0, tokenDefinitionReference, 0, mock(Map.class)
		);

		ParticleGroup pg = ParticleGroup.of(SpunParticle.down(ttp), SpunParticle.up(ttp));
		Atom atom = new Atom(pg, 0L);

		AtomToTokenTransfersMapper tokenTransferTranslator = new AtomToTokenTransfersMapper();
		TestObserver<TokenTransfer> testObserver = TestObserver.create();
		tokenTransferTranslator.map(atom, mock(RadixIdentity.class)).subscribe(testObserver);
		testObserver.assertValue(transfer -> myAddress.equals(transfer.getFrom()) && myAddress.equals(transfer.getTo()));
	}
}