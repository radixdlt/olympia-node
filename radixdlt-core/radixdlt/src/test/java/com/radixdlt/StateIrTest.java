package com.radixdlt;

import com.radixdlt.identifiers.REAddr;
import com.radixdlt.stateir.OlympiaStateIRDeserializer;
import com.radixdlt.utils.UInt128;
import com.radixdlt.utils.UInt256;
import org.bouncycastle.util.encoders.Base64;
import org.junit.Test;
import org.xerial.snappy.Snappy;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class StateIrTest {

	//    57896044618658097711785492504343953926634992332820282019728.792003956564819967
	// 10000000000000000000000000000000000000000000000000000000000000
	@Test
	public void asd() throws Exception {
		final var content = Files.readString(Path.of("end-state.base64"));
		final var data = Base64.decode(content);
		final var uncompressed = Snappy.uncompress(data);
		try (final var bais = new ByteArrayInputStream(uncompressed)) {
			final var state = new OlympiaStateIRDeserializer().deserialize(bais);

			System.out.println("Num balances " + state.balances().size());
			final var max = UInt256.MAX_VALUE.divide(UInt256.TWO).subtract(UInt128.ONE).toBigInt();
			for (var balance : state.balances()) {
				if (balance.amount().compareTo(max) >= 0) {
					System.out.println("Got a balance of " + balance.amount());
				}
			}

			BigInteger[] resourceSupplies = new BigInteger[state.resources().size()];

			for (var balance: state.balances()) {
				if (resourceSupplies[balance.resourceIndex()] == null) {
					resourceSupplies[balance.resourceIndex()] = balance.amount();
				} else {
					resourceSupplies[balance.resourceIndex()] = resourceSupplies[balance.resourceIndex()].add(balance.amount());
				}
			}

			BigInteger largest = BigInteger.ZERO;

			for (var supply: resourceSupplies) {
				if (supply != null && supply.compareTo(largest) >= 0) {
					largest = supply;
				}
				if (supply != null && supply.compareTo(max) >= 0) {
					System.out.println("Got a total supply of of " + supply);
				}
			}

			// 115792089237316195423570985008687907853269984665640564039457584007913129639935
			//                          1 0 0000000000 0000000000 0000000000 0000000000 0000000000 0000000000
			//       limit in scrypto:                                             1 000000000000000000  = 10^18
			System.out.println("UINT256 max " + UInt256.MAX_VALUE.toBigInt());
			System.out.println("Largest supply: " + largest);
		}
	}
}
