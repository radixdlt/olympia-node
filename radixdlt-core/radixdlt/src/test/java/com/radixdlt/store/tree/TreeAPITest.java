package com.radixdlt.store.tree;

import com.radixdlt.store.tree.hash.Keccak256;
import com.radixdlt.store.tree.serialization.rlp.RLPSerializer;
import com.radixdlt.store.tree.storage.InMemoryPMTStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.junit.Assert.assertEquals;

public class TreeAPITest {
	private static final Logger logger = LogManager.getLogger();

	@Test
	public void simpleAddGet() {

		var storage = new InMemoryPMTStorage();
		var tree = new PMT(storage);
		var sub1 = "300126433882d547b3fbb20ca1935879e03a4f75b474546ccf39b4cd03edbe1600000000".getBytes();
		var val1 = "1000000000".getBytes();

		tree.add(sub1, val1);

		var val1back = tree.get(sub1);
		assertEquals(val1, val1back);
	}

	@Test
	public void when_tree_contains_extension_nodes__then_values_can_be_added_and_retrieved() {
		var storage = new InMemoryPMTStorage();
		var tree = new PMT(storage, new Keccak256(), new RLPSerializer(), Duration.of(10, ChronoUnit.MINUTES));

		String verbKey = "646f";
		String verbValue = "verb";
		tree.add(
				Hex.decode(verbKey),
				verbValue.getBytes(StandardCharsets.UTF_8)
		);

		Assert.assertArrayEquals(
				tree.get(Hex.decode(verbKey)),
				verbValue.getBytes(StandardCharsets.UTF_8)
		);

		String puppyKey = "646f67";
		String puppyValue = "puppy";
		tree.add(
				Hex.decode(puppyKey),
				puppyValue.getBytes(StandardCharsets.UTF_8)
		);

		Assert.assertArrayEquals(
				tree.get(Hex.decode(puppyKey)),
				puppyValue.getBytes(StandardCharsets.UTF_8)
		);

		String coinKey = "646f6765";
		String coinValue = "coin";
		tree.add(
				Hex.decode(coinKey),
				coinValue.getBytes(StandardCharsets.UTF_8)
		);

		Assert.assertArrayEquals(
				tree.get(Hex.decode(coinKey)),
				coinValue.getBytes(StandardCharsets.UTF_8)
		);

		String stallionKey = "686f727365";
		String stallionValue = "stallion";
		tree.add(
				Hex.decode(stallionKey),
				stallionValue.getBytes(StandardCharsets.UTF_8)
		);


		Assert.assertArrayEquals(
				tree.get(Hex.decode(verbKey)),
				verbValue.getBytes(StandardCharsets.UTF_8)
		);

		Assert.assertArrayEquals(
				tree.get(Hex.decode(puppyKey)),
				puppyValue.getBytes(StandardCharsets.UTF_8)
		);

		Assert.assertArrayEquals(
				tree.get(Hex.decode(coinKey)),
				coinValue.getBytes(StandardCharsets.UTF_8)
		);

		Assert.assertArrayEquals(
				tree.get(Hex.decode(stallionKey)),
				stallionValue.getBytes(StandardCharsets.UTF_8)
		);
	}
}
