package com.radixdlt.client.core.serialization;

import org.bouncycastle.util.encoders.Base64;
import org.junit.Test;
import org.radix.serialization2.Serialization;
import org.radix.serialization2.client.Serialize;

import com.radixdlt.client.core.address.RadixUniverseConfig;

import static org.junit.Assert.assertEquals;

public class DsonTest {
	@Test
	public void fromDsonTest() {
		Serialization serialization = Serialize.getInstance();
		byte[] originalDson = Base64.decode(
				"v2djcmVhdG9yWCIBA3hanCWf3pmR5E+i+wtWWfKleBrDOQduLb/vcFKOSt9oa2Rl" +
				"c2NyaXB0aW9ueB5UaGUgUmFkaXggZGV2ZWxvcG1lbnQgVW5pdmVyc2VnZ2VuZXNp" +
				"c4O/ZmFjdGlvbmVTVE9SRWVhc3NldL9uY2xhc3NpZmljYXRpb25pY29tbW9kaXR5" +
				"a2Rlc2NyaXB0aW9uaVJhZGl4IFBPV2RpY29uWQaiAYlQTkcNChoKAAAADUlIRFIA" +
				"AAAgAAAAIAgGAAAAc3p69AAAAAlwSFlzAAAuIwAALiMBeKU/dgAAAB1pVFh0U29m" +
				"dHdhcmUAAAAAAEFkb2JlIEltYWdlUmVhZHkGrQKXAAAGKklEQVRYha1Xa1BUVRz/" +
				"nXvPtrCYpuDKspspT1F648oq0IqATqnN+M4XMdPDshk1pyanD02PyQ9liWPaYImM" +
				"MonVjNo0iDx2g03evYRgSQSVx+Ly0HJhl73nnj7gGrirPOI3cz/ce849/9//cf4P" +
				"wjnHSJAkSaxraIwuNJUuMVvKjLUN1hjbdXuw0+kKAAB/P6UjWK22zYuOvGiMN5iT" +
				"jQlF8+ZENlBK2Uhnk/sRkBgTyyprFmTl5KYVmkqXtts6dUySRBACEDJ8M+cA5xAp" +
				"lUKCZ7SmLE7MT9+0Ltugf7pCFEV5zASutrbp9h3M3JmT+/3W7q6e6RAIIAgjKTQI" +
				"WQZkjsCgQPvmDauz39z+yv6ZupA2n3s5515PaVll3IKk5RZMDuGYouV4SDe+Z4qW" +
				"Y3IIj0teUWIpr9L7kuVlgbxCU/L23e8eam5uiYAojk7jkcAYwkJnNx7+fO9rKYsT" +
				"i4cuDSNgKa+K2/rqjuPNzS3hEyZ8CInQ0NmNxzMztizUx1Z6Ebja2qZdm7btVGVV" +
				"zcIJFz6ERFzcfMu3WV+u12k17QBAAcAtSXTfwcxdldW/GP6XcJkD8BHUnlsjiiiv" +
				"qF6074vMnZ988O4eSikjnHNYyqsMz7+Qfran90aQ1/UaJYhCARI0FUQQhnMgBNzh" +
				"gHzj78F3zhE4bar9zDdZKxYtiK2gbrebHj1xMr2nqycIdPza+6etg9/ald75gYpw" +
				"nTkHx/7MO5botndNz8rJfVH/9BM1tL7xUlSBqXTpqO/43ZBliDN1UK5eDkEzA3J3" +
				"z2Ae8EAQvEkJAs4Xlyyz/tUUQQtMJckdtk4thPGZHhx4ICke4sNauMtrcOvjDMA9" +
				"MGQDAb/lGE5CENDeYdMVmkuXUFNpmZFJkjguC3AOIXAqlMuSAMbg+iEfzHoJEO86" +
				"y0fqZpJETaUXjLSuwRoz3sCDLENhiAWdGwmpsQkDlkqAit4m9wVCUFtvfZTaOu0a" +
				"nz/cXSO8ig9AVCoon0sGUSgwkG+GbO8afb0gBLZOu4Y6XS6Vr0UyeRLI7ZzAB9ze" +
				"fpQZ6GNzoYh9AqytA67CktEJHoL+fqeKen3lHGRSACa99xZo2CyAc8g9vXB8ehhS" +
				"bcN//lUooHx2CYQpD6L/bD7YlWuj1/4/YaB+fso+T2NxB4RACJwGIVgNCARiVBgC" +
				"dryMf975EHLvzcG6Hz4bDyQaIPfehCuvCJCYd/CNAJXK30GD1eqOlitXw++YlxDw" +
				"vn7cev8TEKUSwtQpCHj7DSgW6eG3ZR36Dn4NMAZlqhGiZgZc+SZIdQ1jFg7OEaxW" +
				"d9CY6KiLLS1Xwof5lzGwppbBlMplkAAVJn20B/4bV0G6WA+pth7KlGfAnU64fiwA" +
				"73dizDWEc8TMjaqlxgSDOa+geCWT5eEn3PGnAFexBfTkafi/tAmq19PhLquCGPoI" +
				"pN/rMFD56zh8D4gKKhnjDSaaYkwo3B88o7W1tf2Re2ZDxtCfnQs6L3Lw3ofNAgC4" +
				"8orAe2+O3fyyDK1O25psTCii0ZERjamLE/OPZue8AsH7UgAACIHc1QPHga8wedZM" +
				"CFoNWFMzXOYLo0s6XgQ4UpMS8+ZEhF2iCgWV0jevzzr9Y/6qnt7ee5djUYD0x5/o" +
				"+zIbqu3pcJ09D7ndhjHXEM4ROD3Inr5p/TFKqUQBIC72yeotG1Yfyzh0ZDfIfVQi" +
				"BM4z5+Cu/g3y9a6xCfYcwTlP27jmqP6px2s87wCAa23tIWtf3JZbUVEdP2JEy7LP" +
				"AjMiGIPBoC85lXV4gy5E0zGMAABcqKjWb3l1x4nLl5snriMeIjwsLNR6PPPAZsP8" +
				"p6o9n73a8gJTSdJru/YcbrrcHDmRbXl4WKj10Od7t6UYE8zD1nwNCz+XV+kNKStL" +
				"JmowWZj6/E8XKqvnj2ow8eBaW3vIZ18c2Xn85Hdp3fZu9XhGsyB1UOfWDWuO7Xr9" +
				"5QyddtDnd+O+wyljTCir+kV/LOdUWoGpZGlbh+1hJkkUuB2Anhjk/HYnzCEqqKTT" +
				"aK6lJiWeS9u4Ljsu9smqcQ2nQyExJtRb/5pTZC5NMlnKjHX11hjbdbumr98ZAAAq" +
				"fz9HsFrdERMdddGYYDAnPxNfHB0VYb2fYA/+BeLxAk0mNtlqAAAAAElFTkSuQmCC" +
				"Y2lzb2NQT1dlbGFiZWxtUHJvb2Ygb2YgV29ya21tYXhpbXVtX3VuaXRzAGpzZXJp" +
				"YWxpemVyGgO68tBoc2V0dGluZ3MZEABpc3ViX3VuaXRzAGR0eXBlaUNPTU1PRElU" +
				"WWd2ZXJzaW9uGGT/bmNocm9ub1BhcnRpY2xlv2pzZXJpYWxpemVyGkBg0ilqdGlt" +
				"ZXN0YW1wc6JnZGVmYXVsdBsAAAFahyqYAGdleHBpcmVzG3//////////Z3ZlcnNp" +
				"b24YZP9sZGVzdGluYXRpb25zgVECVqurOHBYXwTQFdVa32ALx2Zvd25lcnOBv2Zw" +
				"dWJsaWNYIgEDeFqcJZ/emZHkT6L7C1ZZ8qV4GsM5B24tv+9wUo5K32hqc2VyaWFs" +
				"aXplchogne87Z3ZlcnNpb24YZP9qc2VyaWFsaXplchoAHtFRanNpZ25hdHVyZXO/" +
				"eCA1NmFiYWIzODcwNTg1ZjA0ZDAxNWQ1NWFkZjYwMGJjN79hclghAUn5sVEKr0k0" +
				"P/LXI+o/TxEr9HgloTFRrc8wfFrftmSPYXNYIgEAgfckvcyniLQsAJU3TuJv2WUG" +
				"CcH7StSx8xH5QhtrTEpqc2VyaWFsaXplcjoZ6ldnZ3ZlcnNpb24YZP//Z3ZlcnNp" +
				"b24YZP+/ZmFjdGlvbmVTVE9SRWVhc3NldL9uY2xhc3NpZmljYXRpb25qY3VycmVu" +
				"Y2llc2tkZXNjcmlwdGlvbngZUmFkaXggVGVzdCBjdXJyZW5jeSBhc3NldGRpY29u" +
				"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" +
				"ZC5NMlnKjHX11hjbdbumr98ZAAAqfz9HsFrdERMdddGYYDAnPxNfHB0VYb2fYA/+" +
				"BeLxAk0mNtlqAAAAAElFTkSuQmCCY2lzb2RURVNUZWxhYmVsaVRlc3QgUmFkc21t" +
				"YXhpbXVtX3VuaXRzAGZzY3J5cHS/anNlcmlhbGl6ZXIaILpsKGd2ZXJzaW9uGGT/" +
				"anNlcmlhbGl6ZXIaA7ry0GhzZXR0aW5ncxlQA2lzdWJfdW5pdHMaAAGGoGR0eXBl" +
				"aENVUlJFTkNZZ3ZlcnNpb24YZP9uY2hyb25vUGFydGljbGW/anNlcmlhbGl6ZXIa" +
				"QGDSKWp0aW1lc3RhbXBzomdkZWZhdWx0GwAAAVqHKpgAZ2V4cGlyZXMbf///////" +
				"//9ndmVyc2lvbhhk/2xkZXN0aW5hdGlvbnOBUQJWq6s4cFhfBNAV1VrfYAvHZm93" +
				"bmVyc4G/ZnB1YmxpY1giAQN4Wpwln96ZkeRPovsLVlnypXgawzkHbi2/73BSjkrf" +
				"aGpzZXJpYWxpemVyGiCd7ztndmVyc2lvbhhk/2pzZXJpYWxpemVyGgAe0VFqc2ln" +
				"bmF0dXJlc794IDU2YWJhYjM4NzA1ODVmMDRkMDE1ZDU1YWRmNjAwYmM3v2FyWCIB" +
				"AL+KRBvEvkjLcmVE6KYiufwq1loXkqNaln4JUPB6WfHbYXNYIgEA68qLd6db8Ap1" +
				"j9hCYRH8S73DTno5miXL6GFayvGOwt9qc2VyaWFsaXplcjoZ6ldnZ3ZlcnNpb24Y" +
				"ZP//Z3ZlcnNpb24YZP+/ZmFjdGlvbmVTVE9SRW5jaHJvbm9QYXJ0aWNsZb9qc2Vy" +
				"aWFsaXplchpAYNIpanRpbWVzdGFtcHOhZ2RlZmF1bHQbAAABWocqmABndmVyc2lv" +
				"bhhk/2tjb25zdW1hYmxlc4G/aGFzc2V0X2lkUQLXvTS/5EoY0qp1WjRP4+awbGRl" +
				"c3RpbmF0aW9uc4FRAlarqzhwWF8E0BXVWt9gC8dlbm9uY2UbAAFfFbg592dmb3du" +
				"ZXJzgb9mcHVibGljWCIBA3hanCWf3pmR5E+i+wtWWfKleBrDOQduLb/vcFKOSt9o" +
				"anNlcmlhbGl6ZXIaIJ3vO2d2ZXJzaW9uGGT/aHF1YW50aXR5GwAAWvMQekAAanNl" +
				"cmlhbGl6ZXIaajslh2d2ZXJzaW9uGGT/bWRhdGFQYXJ0aWNsZXOBv2VieXRlc1YB" +
				"UmFkaXguLi4uSnVzdCBJbWFnaW5lanNlcmlhbGl6ZXIaHDz8MGd2ZXJzaW9uGGT/" +
				"bGRlc3RpbmF0aW9uc4FRAlarqzhwWF8E0BXVWt9gC8dqc2VyaWFsaXplchoAHtFR" +
				"anNpZ25hdHVyZXO/eCA1NmFiYWIzODcwNTg1ZjA0ZDAxNWQ1NWFkZjYwMGJjN79h" +
				"clghAS5Hqy7Y+E7XM6JqX4EGEIAS94dK/WEgLIbcgWX71oCzYXNYIgEAgetgaamm" +
				"CWF2y5vWNT+aoZOREOWeG4Q9sMBDvzIv4DJqc2VyaWFsaXplcjoZ6ldnZ3ZlcnNp" +
				"b24YZP//bnRlbXBvcmFsX3Byb29mv2dhdG9tX2lkUQIEU57MLExqDgRgxG7kXU1r" +
				"anNlcmlhbGl6ZXIacY6fQmd2ZXJzaW9uGGRodmVydGljZXOBv2VjbG9jawBqY29t" +
				"bWl0bWVudFghAwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAZW93bmVy" +
				"v2ZwdWJsaWNYIgECIOL2ztjYQ4hLsm3tYnT0p6ak8wgjeFvcd4FX8xoD+qZqc2Vy" +
				"aWFsaXplchogne87Z3ZlcnNpb24YZP9ocHJldmlvdXNRAgAAAAAAAAAAAAAAAAAA" +
				"AABqc2VyaWFsaXplcjo2M2S5aXNpZ25hdHVyZb9hclgiAQCfa+hWoKvzrOR0hqtH" +
				"kLYe1m29CNU2PZNdXybFILp402FzWCIBAPm/pFs8+tSoqXF7IleP6l53RvhulqKy" +
				"fmP55z/mNB8hanNlcmlhbGl6ZXI6GepXZ2d2ZXJzaW9uGGT/anRpbWVzdGFtcHOh" +
				"Z2RlZmF1bHQbAAABZk87HdtndmVyc2lvbhhk//9ndmVyc2lvbhhk/2VtYWdpYxoD" +
				"zYACZG5hbWVsUmFkaXggRGV2bmV0ZHBvcnQZdTBqc2VyaWFsaXplchodWDpFa3Np" +
				"Z25hdHVyZS5yWCIBAI8ljsnkTqJVdUwMR76m2ehWIxaeCEZqcVCVR3Qe9QH2a3Np" +
				"Z25hdHVyZS5zWCIBAIAHfvylFm7W1VTIGdY1Imly9buKNsIi7s60ZgoPYI46aXRp" +
				"bWVzdGFtcBsAAAFahyqYAGR0eXBlAmd2ZXJzaW9uGGT/"
			);
		RadixUniverseConfig universeFromDson = serialization.fromDson(originalDson, RadixUniverseConfig.class);
		assertEquals(63799298, universeFromDson.getMagic());
		assertEquals(3, universeFromDson.getGenesis().size());
	}
}
