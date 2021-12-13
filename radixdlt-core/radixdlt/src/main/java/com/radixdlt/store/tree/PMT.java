package com.radixdlt.store.tree;

import com.radixdlt.store.tree.hash.HashFunction;
import com.radixdlt.store.tree.hash.SHA256;
import com.radixdlt.store.tree.serialization.PMTNodeSerializer;
import com.radixdlt.store.tree.serialization.rlp.RLPSerializer;
import com.radixdlt.store.tree.storage.PMTCache;
import com.radixdlt.store.tree.storage.PMTStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public class PMT {

	private static final Logger log = LogManager.getLogger();
	public static final int CACHE_MAXIMUM_SIZE = 1000;

	private final PMTStorage db;
	private final PMTCache cache;
	private final HashFunction hashFunction;
	private final PMTNodeSerializer pmtNodeSerializer;
	private PMTNode root;

	// API:
	//add
	//update
	//delete
	//get
	//
	//get_root
	//get_proof
	//verify_proof

	public PMT(PMTStorage db) {
		this(db, new SHA256(), new RLPSerializer(), Duration.of(10, ChronoUnit.MINUTES));
	}

	public PMT(PMTStorage db, HashFunction hashFunction, PMTNodeSerializer pmtNodeSerializer, Duration cacheExpiryAfter) {
		this.db = db;
		this.cache = new PMTCache(
			CACHE_MAXIMUM_SIZE,
			cacheExpiryAfter,
			this::nodeLoader
		);
		this.hashFunction = hashFunction;
		this.pmtNodeSerializer = pmtNodeSerializer;
	}

	public byte[] represent(PMTNode node) {
		return represent(node, this.hashFunction, this.pmtNodeSerializer);
	}

	public static byte[] represent(PMTNode node, HashFunction hashFunction, PMTNodeSerializer pmtNodeSerializer) {
		var ser = pmtNodeSerializer.serialize(node);
		if (hasDbRepresentation(ser)) {
			return hashFunction.hash(ser);
		} else {
			return ser;
		}
	}

	public static boolean hasDbRepresentation(byte[] serializedNode) {
		// INFO: the DB_SIZE_CONDITION relates to hash size
		return serializedNode.length >= PMTNode.DB_SIZE_COND;
	}

	public byte[] get(byte[] key) {
		var pmtKey = new PMTKey(PMTPath.intoNibbles(key));

		if (this.root != null) {
			var acc = this.root.getValue(pmtKey, new PMTAcc(), this::read);
			if (acc.notFound()) {
				log.debug("Not found key: {} for root: {}",
					TreeUtils.toHexString(key),
					TreeUtils.toHexString(root == null ? null : represent(root)));
				// TODO XXX: what shall we return for not found? Maybe lets wrap everything in Option?
				return new byte[0];
			} else {
				return acc.getRetVal().getValue();
			}
		} else {
			log.debug("Tree empty when key: {}",
				TreeUtils.toHexString(key));
			// TODO XXX: what shall we return for empty Maybe lets wrap everything in Option?
			return null;
		}
	}

	public byte[] add(byte[] key, byte[] val) {

		try {
			var pmtKey = new PMTKey(PMTPath.intoNibbles(key));

			if (this.root != null) {
				var acc = this.root.insertNode(pmtKey, val, new PMTAcc(), this::represent, this::read);
				this.root = acc.getTip();
				acc.getNewNodes().stream()
					.filter(Objects::nonNull)
					.forEach(sanitizedAcc -> {
						this.cache.put(represent(sanitizedAcc), sanitizedAcc);
						byte[] serialisedNode = this.pmtNodeSerializer.serialize(sanitizedAcc);
						if (sanitizedAcc == this.root || hasDbRepresentation(serialisedNode)) {
							this.db.save(hash(serialisedNode), serialisedNode);
						}
					});
			} else {
				PMTNode nodeRoot = new PMTLeaf(pmtKey, val);
				this.root = nodeRoot;
				byte[] serialisedNode = this.pmtNodeSerializer.serialize(nodeRoot);
				this.cache.put(represent(nodeRoot), nodeRoot);
				this.db.save(hash(nodeRoot), serialisedNode);
			}
		} catch (Exception e) {
			log.error("PMT operation failure: {} for: {} {} {}",
				e.getMessage(),
				TreeUtils.toHexString(key),
				TreeUtils.toHexString(val),
				TreeUtils.toHexString(root == null ? null : hash(root)));
			throw e;
		}
		return root == null ? null : hash(root);
	}

	private PMTNode read(byte[] key) {
		return this.cache.get(key);
	}

	private PMTNode nodeLoader(byte[] key) {
		byte[] node;
		if (hasDbRepresentation(key)) {
			node = db.read(key);
		} else {
			node = key;
		}
		return pmtNodeSerializer.deserialize(node);
	}

	private byte[] hash(byte[] serialized) {
		return this.hashFunction.hash(serialized);
	}

	private byte[] hash(PMTNode node) {
		return hash(this.pmtNodeSerializer.serialize(node));
	}

}
