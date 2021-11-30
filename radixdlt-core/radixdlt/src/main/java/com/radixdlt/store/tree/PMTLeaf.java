package com.radixdlt.store.tree;

import com.radixdlt.store.tree.serialization.rlp.RLP;
import org.spongycastle.util.Arrays;

public class PMTLeaf extends PMTNode {

	private static final int EVEN_PREFIX = 2;
	private static final int ODD_PREFIX = 3;

	PMTLeaf(PMTKey allNibbles, byte[] newValue) {
		this(null, allNibbles, newValue);
	}

	PMTLeaf(PMTKey branchNibble, PMTKey keyNibbles, byte[] newValue) {
		this.nodeType = NodeType.LEAF;
		this.branchNibble = branchNibble;
		this.keyNibbles = keyNibbles;
		this.value = newValue;
	}

	public byte[] serialize() {
		// INFO: leaf can have empty key. It's because value may not fit branches' hash pointer field

		// XXX TODO: this is probably wrong on bit level?!
		var prefixedKey = fromNibblesToBytes(
			TreeUtils.applyPrefix(this.getKey().getKey(), ODD_PREFIX, EVEN_PREFIX)
		);
		// TODO: serialize, RLP?
		this.serialized = RLP.encodeList(
				RLP.encodeElement(prefixedKey),
				RLP.encodeElement(value)
		);
		return this.serialized;
	}

	public byte[] fromNibblesToBytes(byte[] nibbles) {
		byte[] bytes = new byte[0];
		for (int i = 0; i < nibbles.length; i += 2) {
			byte b = (byte) ((byte) (nibbles[i]<<4) + (nibbles[i+1]));
			bytes = Arrays.append(bytes, b);
		}
		return bytes;
	}
}
