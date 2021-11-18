package com.radixdlt.store.tree;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.nio.ByteBuffer;

public class PMTTest {

	private static final Logger logger = LogManager.getLogger();

	@Test
	public void serializationPrefix() {

		var p1 = ByteBuffer.allocate(8).putInt(2).get();
		//var p2 = ByteBuffer.allocate(4).putInt(3).get();

		logger.info("8 bit even prefix {}", p1);
		//logger.info("4 bit even prefix {}", p2);

		int high1 = (p1 & 0xf0) >> 4;
		int low1 = p1 & 0xf;

		//int high2 = (p2 & 0xf0) >> 4;
		//int low2 = p2 & 0xf;


		logger.info("8 bit even prefix in nibbles {} {}", high1, low1);
		//logger.info("4 bit even prefix in nibbles {} {}", high2, low2);
	}

	@Test
	public void commonPrefix() {

		// example Substates
		var path1 = "300126433882d547b3fbb20ca1935879e03a4f75b474546ccf39b4cd03edbe1600000000".getBytes();
		var path2 = "300126433882d547b3fbb20ca1935879e03a4f75b474546ccf39b4cd03edbe1600000001".getBytes();

		logger.info("path1: {}", path1);
		logger.info("paxth2xxxx: {}", path2);

		var lengthP1 = path1.length;
		var lengthP2 = path2.length;

		var nibsPath1 = new int[lengthP1*2];
		var nibsIndex = 0;
		for(byte b : path1) {
			logger.info("path1: {}", b);
			int high = (b & 0xf0) >> 4;
			int low = b & 0xf;

			nibsPath1[nibsIndex] = high;
			nibsPath1[nibsIndex+1] = low;

			logger.info("h: {} {} ", high);
			logger.info("l: {}", low);
			System.out.println("..");

			nibsIndex=nibsIndex+2;

			logger.info("p1: index: {} cur: {}",nibsIndex, nibsPath1);
		}

		var nibsPath2 = new int[lengthP2*2];
		nibsIndex = 0;
		for(byte b : path2) {
			logger.info("path2: {}", b);
			int high = (b & 0xf0) >> 4;
			int low = b & 0xf;

			nibsPath2[nibsIndex] = high;
			nibsPath2[nibsIndex+1] = low;

			logger.info("h: {}", high);
			logger.info("l: {}", low);
			System.out.println("..");

			nibsIndex=nibsIndex+2;
		}

		logger.info("p1: {}", nibsPath1);
		logger.info("p2: {}", nibsPath2);

		var smaller = Math.min(nibsPath1.length, nibsPath2.length);
		for (int i=0; i < smaller; i++) {
			logger.info("index: {}, val: {} {}", i, nibsPath1[i], nibsPath2[i]);
			if (nibsPath1[i] == nibsPath2[i]) {
				logger.info("YES");
			} else {
				logger.info("NO");
				break;
			}
		}
	}
}
