package org.radix.serialization2;

import com.radixdlt.client.core.atoms.RadixHash;
import org.junit.Test;
import org.radix.serialization2.client.Serialize;

import com.radixdlt.client.core.atoms.Atom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AtomSerializationTest {

	@Test
	public void testAtomSerialization() {
		String atomString = "{\"dataParticles\":[{\"bytes\":\":byt:0AGgg7FFonQOWyDN0CbvbSED9Lwy7NQs17U34hb0pu+ds"
				+ "/EIzxoqZ6UkYEWoYYK2Sx0AAAAQ7DvCu1Mw3rAsMW50OroUR26DLNRsdj+3eMYf1KNgMH8MgDlQfVkSDL4/u03SP"
				+ "cro\",\"serializer\":473758768,\"version\":100},{\"metaData\":{\"application\":\":str:en"
				+ "cryptor\",\"contentType\":\":str:application/json\"},\"bytes\":\":byt:WyJvWE1xTGcvWWtiNS"
				+ "tZTjdHUkNKKzFTRUMxdGxGQjhlZ01TdDRrdFM4d2xBbitoT2NlRTVBK25PaGRiWGZGbjd5K2FZQUFBQXd5bFI0Sl"
				+ "prVjVmZG15RkdlaTM0bFh5b2VJcDNscnBaUnZtSFZNbHY3elBWWFQyR3JJM1dIV1hjbm9GOGhBZHUvWjNQc001OE"
				+ "pJYURHMWtjejBGTnVFYm5STjdsZmNEVU8yUlUrR1lSSi9kWT0iLCIzNnFydmlvWEMzY2xUUldhcFFmSVlDRUNvek"
				+ "FtSkppdjRYUHRUNC9MejNOZEtHaFI3VzFsaGozaU5pbm5wUC9qY0JFQUFBQXdhdVdBeElMSUNqVUlsRGR5dnNBOE"
				+ "VRemZCLzhXUFI3OXlmNC9CdjdXeWtPSS85cXhYWWpacjlMN05pRFRhcGFhUU1VaEtpeEpuRldORDZtU05kQVdpMS"
				+ "9BbGYvbjQxVjQ5dFJzejNZZUQ4bz0iXQ==\",\"serializer\":473758768,\"version\":100}],\"consum"
				+ "ables\":[{\"quantity\":10000000,\"destinations\":[\":uid:c0a9dd141e324d6042372990a1cc195"
				+ "6\"],\"serializer\":318720611,\"owners\":[{\"public\":\":byt:AtdZJj84TD78JkfAvbI2e4YA0GL"
				+ "HIAVTCOzws05Fowu8\",\"serializer\":547221307,\"version\":100}],\"asset_id\":\":uid:d7bd3"
				+ "4bfe44a18d2aa755a344fe3e6b0\",\"version\":100,\"nonce\":540608639615663},{\"quantity\":9"
				+ "9999990000000,\"destinations\":[\":uid:56abab3870585f04d015d55adf600bc7\"],\"serializer"
				+ "\":318720611,\"owners\":[{\"public\":\":byt:A3hanCWf3pmR5E+i+wtWWfKleBrDOQduLb/vcFKOSt9o"
				+ "\",\"serializer\":547221307,\"version\":100}],\"asset_id\":\":uid:d7bd34bfe44a18d2aa755a"
				+ "344fe3e6b0\",\"version\":100,\"nonce\":540608639645877},{\"quantity\":414,\"destinations"
				+ "\":[\":uid:56abab3870585f04d015d55adf600bc7\"],\"serializer\":-1463653224,\"owners\":[{"
				+ "\"public\":\":byt:A3hanCWf3pmR5E+i+wtWWfKleBrDOQduLb/vcFKOSt9o\",\"serializer\":54722130"
				+ "7,\"version\":100}],\"asset_id\":\":uid:229c9d7905761d24ea9fafcff64d3d49\",\"version\":1"
				+ "00,\"nonce\":540608642610988}],\"destinations\":[\":uid:56abab3870585f04d015d55adf600bc7"
				+ "\",\":uid:c0a9dd141e324d6042372990a1cc1956\"],\"serializer\":2019665,\"action\":\":str:S"
				+ "TORE\",\"consumers\":[{\"quantity\":100000000000000,\"destinations\":[\":uid:56abab38705"
				+ "85f04d015d55adf600bc7\"],\"serializer\":214856694,\"owners\":[{\"public\":\":byt:A3hanCW"
				+ "f3pmR5E+i+wtWWfKleBrDOQduLb/vcFKOSt9o\",\"serializer\":547221307,\"version\":100}],\"ass"
				+ "et_id\":\":uid:d7bd34bfe44a18d2aa755a344fe3e6b0\",\"version\":100,\"nonce\":508147166233"
				+ "960}],\"version\":100,\"signatures\":{\"56abab3870585f04d015d55adf600bc7\":{\"r\":\":byt"
				+ ":AL3/nYW7FGVRNndA3x1tRBS4vfPKrGHLKTGwcDEhYCcp\",\"s\":\":byt:AIDk+93m2xSNpBO6WO+Pim25k0u"
				+ "yqaBf8E1TwRhOZfcm\",\"serializer\":-434788200,\"version\":100}},\"chronoParticle\":{\"ti"
				+ "mestamps\":{\"default\":1539178735739},\"serializer\":1080087081,\"version\":100}}";
		Atom atom = Serialize.getInstance().fromJson(atomString, Atom.class);
		assertNotNull(atom);
		assertEquals("1b1cff72cb4f79d2eb50b5fb2777d65bebb5cad146e2006f25cde7a53445ffe7", atom.getHash().toHexString());
	}

}
