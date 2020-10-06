package com.radixdlt.consensus;

import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.Hash;
import nl.jqno.equalsverifier.EqualsVerifier;

import org.junit.Test;
import org.powermock.reflect.Whitebox;
import org.radix.serialization.SerializeObject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class SyncInfoTest extends SerializeObject<SyncInfo> {
	public SyncInfoTest() {
		super(SyncInfo.class, SyncInfoTest::get);
	}

	private static SyncInfo get() {
		View view = View.of(1234567891L);
		Hash id = Hash.random();

		LedgerHeader ledgerHeader = LedgerHeader.genesis(Hash.ZERO_HASH);
		BFTHeader header = new BFTHeader(view, id, ledgerHeader);
		BFTHeader parent = new BFTHeader(View.of(1234567890L), Hash.random(), ledgerHeader);
		BFTHeader commit = new BFTHeader(View.of(1234567889L), Hash.random(), ledgerHeader);
		VoteData voteData = new VoteData(header, parent, commit);
		QuorumCertificate qc = new QuorumCertificate(voteData, new TimestampedECDSASignatures());
		return SyncInfo.from(qc, qc);
	}

	@Test
	public void when_created_with_equal_qcs__highest_committed_is_elided() {
		QuorumCertificate qc = mock(QuorumCertificate.class);
		SyncInfo syncInfo = SyncInfo.from(qc, qc);
		QuorumCertificate storedCommitQC = Whitebox.getInternalState(syncInfo, "highestCommittedQC");
		assertThat(storedCommitQC).isNull();
		assertThat(syncInfo.highestQC()).isEqualTo(qc);
		assertThat(syncInfo.highestCommittedQC()).isEqualTo(qc);
	}

	@Test
	public void when_created_with_unequal_qcs__highest_committed_is_stored() {
		QuorumCertificate qc = mock(QuorumCertificate.class);
		QuorumCertificate cqc = mock(QuorumCertificate.class);
		SyncInfo syncInfo = SyncInfo.from(qc, cqc);
		QuorumCertificate storedCommitQC = Whitebox.getInternalState(syncInfo, "highestCommittedQC");
		assertThat(storedCommitQC).isEqualTo(cqc);
		assertThat(syncInfo.highestQC()).isEqualTo(qc);
		assertThat(syncInfo.highestCommittedQC()).isEqualTo(cqc);
	}

	@Test
	public void sensibleToString() {
		QuorumCertificate qc = mock(QuorumCertificate.class);
		QuorumCertificate cqc = mock(QuorumCertificate.class);
		SyncInfo syncInfo1 = SyncInfo.from(qc, cqc);

		String s1 = syncInfo1.toString();
		assertThat(s1)
			.contains(SyncInfo.class.getSimpleName())
			.contains(qc.toString())
			.contains(cqc.toString());

		SyncInfo syncInfo2 = SyncInfo.from(qc, qc);
		String s2 = syncInfo2.toString();
		assertThat(s2)
			.contains(SyncInfo.class.getSimpleName())
			.contains(qc.toString())
			.contains("<same>");
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(SyncInfo.class)
			.verify();
	}
}
