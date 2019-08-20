package com.radixdlt.tempo.store;

import org.junit.Test;

import static org.junit.Assert.*;

public class CommitmentBatchTest {
	@Test
	public void testPushLast() {
		CommitmentBatch batch1 = createCommitmentBatch(0, 10);
		CommitmentBatch batch2 = createCommitmentBatch(2, 10);
	}

	private CommitmentBatch createCommitmentBatch(int size, int capacity) {
		return null;
	}
}