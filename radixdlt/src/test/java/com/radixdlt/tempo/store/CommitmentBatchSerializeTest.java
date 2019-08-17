package com.radixdlt.tempo.store;

import com.radixdlt.crypto.Hash;
import org.radix.serialization.SerializeMessageObject;

import static org.junit.Assert.*;

public class CommitmentBatchTest extends SerializeMessageObject<CommitmentBatch> {
	public CommitmentBatchTest() {
		super(CommitmentBatch.class, () -> new CommitmentBatch(
			new Hash[]{Hash.random()},
			new long[]{7L}
		));
	}
}