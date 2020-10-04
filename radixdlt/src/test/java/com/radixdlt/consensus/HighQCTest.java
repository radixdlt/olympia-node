package com.radixdlt.consensus;

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;

import org.junit.Test;
import org.powermock.reflect.Whitebox;
import org.radix.serialization.SerializeObject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class HighQCTest extends SerializeObject<HighQC> {
	public HighQCTest() {
		super(HighQC.class, HighQCTest::get);
	}

	private static HighQC get() {
		View view = View.of(1234567891L);
		HashCode id = HashUtils.random256();

		LedgerHeader ledgerHeader = LedgerHeader.genesis(HashUtils.zero256(), null);
		BFTHeader header = new BFTHeader(view, id, ledgerHeader);
		BFTHeader parent = new BFTHeader(View.of(1234567890L), HashUtils.random256(), ledgerHeader);
		BFTHeader commit = new BFTHeader(View.of(1234567889L), HashUtils.random256(), ledgerHeader);
		VoteData voteData = new VoteData(header, parent, commit);
		QuorumCertificate qc = new QuorumCertificate(voteData, new TimestampedECDSASignatures());
		return HighQC.from(qc, qc);
	}

	@Test
	public void when_created_with_equal_qcs__highest_committed_is_elided() {
		QuorumCertificate qc = mock(QuorumCertificate.class);
		HighQC syncInfo = HighQC.from(qc, qc);
		QuorumCertificate storedCommitQC = Whitebox.getInternalState(syncInfo, "highestCommittedQC");
		assertThat(storedCommitQC).isNull();
		assertThat(syncInfo.highestQC()).isEqualTo(qc);
		assertThat(syncInfo.highestCommittedQC()).isEqualTo(qc);
	}

	@Test
	public void when_created_with_unequal_qcs__highest_committed_is_stored() {
		QuorumCertificate qc = mock(QuorumCertificate.class);
		QuorumCertificate cqc = mock(QuorumCertificate.class);
		HighQC syncInfo = HighQC.from(qc, cqc);
		QuorumCertificate storedCommitQC = Whitebox.getInternalState(syncInfo, "highestCommittedQC");
		assertThat(storedCommitQC).isEqualTo(cqc);
		assertThat(syncInfo.highestQC()).isEqualTo(qc);
		assertThat(syncInfo.highestCommittedQC()).isEqualTo(cqc);
	}

	@Test
	public void sensibleToString() {
		QuorumCertificate qc = mock(QuorumCertificate.class);
		QuorumCertificate cqc = mock(QuorumCertificate.class);
		HighQC syncInfo1 = HighQC.from(qc, cqc);

		String s1 = syncInfo1.toString();
		assertThat(s1)
			.contains(HighQC.class.getSimpleName())
			.contains(qc.toString())
			.contains(cqc.toString());

		HighQC syncInfo2 = HighQC.from(qc, qc);
		String s2 = syncInfo2.toString();
		assertThat(s2)
			.contains(HighQC.class.getSimpleName())
			.contains(qc.toString())
			.contains("<same>");
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(HighQC.class)
			.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
			.verify();
	}
}
