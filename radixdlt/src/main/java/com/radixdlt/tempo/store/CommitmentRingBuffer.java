package com.radixdlt.tempo.store;

import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.crypto.Hash;

import java.util.Arrays;
import java.util.Map;

public final class CommitmentRingBuffer {
	private final EvictingQueue<Hash> commitmentBuffer;
	private final int bufferSize;

	public CommitmentRingBuffer(int bufferSize) {
		this.commitmentBuffer = new EvictingQueue<>(bufferSize);
		this.bufferSize = bufferSize;
	}

	public Map<Long, byte[]> push(CommitmentBatch batch) {
		ImmutableMap.Builder<Long, byte[]> positionAndAids = ImmutableMap.builder();
		Hash[] commitments = batch.getCommitments();
		long[] positions = batch.getPositions();
		for (int i = 0; i < batch.size(); i++) {
			push(commitments[i]);
			// if enough commitments, create aids
			if (commitmentBuffer.size() >= bufferSize) {
				byte[] aid = fromLast(null);
				positionAndAids.put(positions[i], aid);
			}
		}
		return positionAndAids.build();
	}

	public void push(Hash commitment) {
		commitmentBuffer.add(commitment);
	}

	public byte[] fromLast(byte[] buffer) {
		if (buffer == null) {
			buffer = new byte[commitmentBuffer.size()];
		}

		Arrays.fill(buffer, (byte) 0);
		// fill buffer with bits from previous commitments
		int i = 0;
		for (Hash commitment : commitmentBuffer) {
			int elementIndex = i >>> 3;
			int bitIndex = i & 7;
			byte[] commitmentBytes = commitment.toByteArray();
			buffer[elementIndex] |= (commitmentBytes[elementIndex] & 0xff) & (1 << bitIndex);
			i++;
		}
		return buffer;
	}
}
