package com.radixdlt.store.tree;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

public class TreeAPITest {
	private static final Logger logger = LogManager.getLogger();

	@Test
	public void addTest() {

		var storage = new PMTCachedStorage();
		var tree = new PMT(storage);
		var sub1 = "300126433882d547b3fbb20ca1935879e03a4f75b474546ccf39b4cd03edbe1600000000".getBytes();
		var val1 = "1000000000".getBytes();

		tree.add(sub1, val1);
	}
}
