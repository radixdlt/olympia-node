package com.radixdlt.client.core.serialization;

import org.bouncycastle.util.encoders.Base64;
import org.junit.Test;

import com.google.gson.JsonElement;
import com.radixdlt.client.core.address.RadixUniverseConfig;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class DsonTest {
	@Test
	public void fromDsonTest() {
		Dson dson = Dson.getInstance();
		byte[] originalDson = Base64.decode(
				"gbh+B2NyZWF0b3JAIQN4Wpwln96ZkeRPovsLVlnypXgawzkHbi2/73BSjkrfaAtkZXNjcmlwdGlvbkEe"
			  + "VGhlIFJhZGl4IGRldmVsb3BtZW50IFVuaXZlcnNlB2dlbmVzaXOAt0KBqUgGYWN0aW9uQQVTVE9SRQVh"
			  + "c3NldIGnbg5jbGFzc2lmaWNhdGlvbkEJY29tbW9kaXR5C2Rlc2NyaXB0aW9uQQlSYWRpeCBQT1cEaWNv"
			  + "bkCmoYlQTkcNChoKAAAADUlIRFIAAAAgAAAAIAgGAAAAc3p69AAAAAlwSFlzAAAuIwAALiMBeKU/dgAA"
			  + "AB1pVFh0U29mdHdhcmUAAAAAAEFkb2JlIEltYWdlUmVhZHkGrQKXAAAGKklEQVRYha1Xa1BUVRz/nXvP"
			  + "trCYpuDKspspT1F648oq0IqATqnN+M4XMdPDshk1pyanD02PyQ9liWPaYImMMonVjNo0iDx2g03evYRg"
			  + "SQSVx+Ly0HJhl73nnj7gGrirPOI3cz/ce849/9//cf4PwjnHSJAkSaxraIwuNJUuMVvKjLUN1hjbdXuw"
			  + "0+kKAAB/P6UjWK22zYuOvGiMN5iTjQlF8+ZENlBK2Uhnk/sRkBgTyyprFmTl5KYVmkqXtts6dUySRBAC"
			  + "EDJ8M+cA5xAplUKCZ7SmLE7MT9+0Ltugf7pCFEV5zASutrbp9h3M3JmT+/3W7q6e6RAIIAgjKTQIWQZk"
			  + "jsCgQPvmDauz39z+yv6ZupA2n3s5515PaVll3IKk5RZMDuGYouV4SDe+Z4qWY3IIj0teUWIpr9L7kuVl"
			  + "gbxCU/L23e8eam5uiYAojk7jkcAYwkJnNx7+fO9rKYsTi4cuDSNgKa+K2/rqjuPNzS3hEyZ8CInQ0NmN"
			  + "xzMztizUx1Z6Ebja2qZdm7btVGVVzcIJFz6ERFzcfMu3WV+u12k17QBAAcAtSXTfwcxdldW/GP6XcJkD"
			  + "8BHUnlsjiiivqF6074vMnZ988O4eSikjnHNYyqsMz7+Qfran90aQ1/UaJYhCARI0FUQQhnMgBNzhgHzj"
			  + "78F3zhE4bar9zDdZKxYtiK2gbrebHj1xMr2nqycIdPza+6etg9/ald75gYpwnTkHx/7MO5botndNz8rJ"
			  + "fVH/9BM1tL7xUlSBqXTpqO/43ZBliDN1UK5eDkEzA3J3z2Ae8EAQvEkJAs4Xlyyz/tUUQQtMJckdtk4t"
			  + "hPGZHhx4ICke4sNauMtrcOvjDMA9MGQDAb/lGE5CENDeYdMVmkuXUFNpmZFJkjguC3AOIXAqlMuSAMbg"
			  + "+iEfzHoJEO86y0fqZpJETaUXjLSuwRoz3sCDLENhiAWdGwmpsQkDlkqAit4m9wVCUFtvfZTaOu0anz/c"
			  + "XSO8ig9AVCoon0sGUSgwkG+GbO8afb0gBLZOu4Y6XS6Vr0UyeRLI7ZzAB9zefpQZ6GNzoYh9AqytA67C"
			  + "ktEJHoL+fqeKen3lHGRSACa99xZo2CyAc8g9vXB8ehhSbcN//lUooHx2CYQpD6L/bD7YlWuj1/4/YaB+"
			  + "fso+T2NxB4RACJwGIVgNCARiVBgCdryMf975EHLvzcG6Hz4bDyQaIPfehCuvCJCYd/CNAJXK30GD1eqO"
			  + "litXw++YlxDwvn7cev8TEKUSwtQpCHj7DSgW6eG3ZR36Dn4NMAZlqhGiZgZc+SZIdQ1jFg7OEaxWd9CY"
			  + "6KiLLS1Xwof5lzGwppbBlMplkAAVJn20B/4bV0G6WA+pth7KlGfAnU64fiwA73dizDWEc8TMjaqlxgSD"
			  + "Oa+geCWT5eEn3PGnAFexBfTkafi/tAmq19PhLquCGPoIpN/rMFD56zh8D4gKKhnjDSaaYkwo3B88o7W1"
			  + "tf2Re2ZDxtCfnQs6L3Lw3ofNAgC48orAe2+O3fyyDK1O25psTCii0ZERjamLE/OPZue8AsH7UgAACIHc"
			  + "1QPHga8wedZMCFoNWFMzXOYLo0s6XgQ4UpMS8+ZEhF2iCgWV0jevzzr9Y/6qnt7ee5djUYD0x5/o+zIb"
			  + "qu3pcJ09D7ndhjHXEM4ROD3Inr5p/TFKqUQBIC72yeotG1Yfyzh0ZDfIfVQiBM4z5+Cu/g3y9a6xCfYc"
			  + "wTlP27jmqP6px2s87wCAa23tIWtf3JZbUVEdP2JEy7LPAjMiGIPBoC85lXV4gy5E0zGMAABcqKjWb3l1"
			  + "x4nLl5snriMeIjwsLNR6PPPAZsP8p6o9n73a8gJTSdJru/YcbrrcHDmRbXl4WKj10Od7t6UYE8zD1nwN"
			  + "Cz+XV+kNKStLJmowWZj6/E8XKqvnj2ow8eBaW3vIZ18c2Xn85Hdp3fZu9XhGsyB1UOfWDWuO7Xr95Qyd"
			  + "dtDnd+O+wyljTCir+kV/LOdUWoGpZGlbh+1hJkkUuB2Anhjk/HYnzCEqqKTTaK6lJiWeS9u4Ljsu9smq"
			  + "cQ2nQyExJtRb/5pTZC5NMlnKjHX11hjbdbumr98ZAAAqfz9HsFrdERMdddGYYDAnPxNfHB0VYb2fYA/+"
			  + "BeLxAk0mNtlqAAAAAElFTkSuQmCCA2lzb0EDUE9XBWxhYmVsQQ1Qcm9vZiBvZiBXb3JrDW1heGltdW1f"
			  + "dW5pdHMgCAAAAAAAAAAACnNlcmlhbGl6ZXIgCAAAAAADuvLQCHNldHRpbmdzIAgAAAAAAAAQAAlzdWJf"
			  + "dW5pdHMgCAAAAAAAAAAABHR5cGVBCUNPTU1PRElUWQd2ZXJzaW9uIAgAAAAAAAAAZA5jaHJvbm9QYXJ0"
			  + "aWNsZYFYCnNlcmlhbGl6ZXIgCAAAAABAYNIpCnRpbWVzdGFtcHOBJAdkZWZhdWx0IAgAAAFahyqYAAdl"
			  + "eHBpcmVzIAh//////////wd2ZXJzaW9uIAgAAAAAAAAAZAxkZXN0aW5hdGlvbnOAEiEQVqurOHBYXwTQ"
			  + "FdVa32ALxwJpZCEQIpydeQV2HSTqn6/P9k09SQZvd25lcnOAU4FRBnB1YmxpY0AhA3hanCWf3pmR5E+i"
			  + "+wtWWfKleBrDOQduLb/vcFKOSt9oCnNlcmlhbGl6ZXIgCAAAAAAgne87B3ZlcnNpb24gCAAAAAAAAABk"
			  + "CnNlcmlhbGl6ZXIgCAAAAAAAHtFRCnNpZ25hdHVyZXOBlCA1NmFiYWIzODcwNTg1ZjA0ZDAxNWQ1NWFk"
			  + "ZjYwMGJjN4FxAXJAIQCXJ30dSsUXOX46+hxzSQtbuPmstNTQU4OseZX0kTs7VgFzQCEA4EC3S511yYTu"
			  + "z00E4DMVYr36MyFhlp+un7gjkea2EpYKc2VyaWFsaXplciAI/////+YVqJgHdmVyc2lvbiAIAAAAAAAA"
			  + "AGQHdmVyc2lvbiAIAAAAAAAAAGSBqYQGYWN0aW9uQQVTVE9SRQVhc3NldIGnqw5jbGFzc2lmaWNhdGlv"
			  + "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"
			  + "ITEm1Fv/mlNkLk0yWcqMdfXWGNt1u6av3xkAACp/P0ewWt0REx110ZhgMCc/E18cHRVhvZ9gD/4F4vEC"
			  + "TSY22WoAAAAASUVORK5CYIIDaXNvQQRURVNUBWxhYmVsQQlUZXN0IFJhZHMNbWF4aW11bV91bml0cyAI"
			  + "AAAAAAAAAAAGc2NyeXB0gScKc2VyaWFsaXplciAIAAAAACC6bCgHdmVyc2lvbiAIAAAAAAAAAGQKc2Vy"
			  + "aWFsaXplciAIAAAAAAO68tAIc2V0dGluZ3MgCAAAAAAAAFADCXN1Yl91bml0cyAIAAAAAAABhqAEdHlw"
			  + "ZUEIQ1VSUkVOQ1kHdmVyc2lvbiAIAAAAAAAAAGQOY2hyb25vUGFydGljbGWBWApzZXJpYWxpemVyIAgA"
			  + "AAAAQGDSKQp0aW1lc3RhbXBzgSQHZGVmYXVsdCAIAAABWocqmAAHZXhwaXJlcyAIf/////////8HdmVy"
			  + "c2lvbiAIAAAAAAAAAGQMZGVzdGluYXRpb25zgBIhEFarqzhwWF8E0BXVWt9gC8cCaWQhENe9NL/kShjS"
			  + "qnVaNE/j5rAGb3duZXJzgFOBUQZwdWJsaWNAIQN4Wpwln96ZkeRPovsLVlnypXgawzkHbi2/73BSjkrf"
			  + "aApzZXJpYWxpemVyIAgAAAAAIJ3vOwd2ZXJzaW9uIAgAAAAAAAAAZApzZXJpYWxpemVyIAgAAAAAAB7R"
			  + "UQpzaWduYXR1cmVzgZMgNTZhYmFiMzg3MDU4NWYwNGQwMTVkNTVhZGY2MDBiYzeBcAFyQCASE0oaPiBv"
			  + "sR3Sdd3J6BvlhqLYoM7x/57NDJoy4aMxZgFzQCEA6HFvUBRolu8k15QuK4wiesFWSQMt4ShFp7FLJCq8"
			  + "cMcKc2VyaWFsaXplciAI/////+YVqJgHdmVyc2lvbiAIAAAAAAAAAGQHdmVyc2lvbiAIAAAAAAAAAGSB"
			  + "pG0GYWN0aW9uQQVTVE9SRQ5jaHJvbm9QYXJ0aWNsZYFGCnNlcmlhbGl6ZXIgCAAAAABAYNIpCnRpbWVz"
			  + "dGFtcHOBEgdkZWZhdWx0IAgAAAFahyqYAAd2ZXJzaW9uIAgAAAAAAAAAZAtjb25zdW1hYmxlc4Cg5YGg"
			  + "4ghhc3NldF9pZCEQ1700v+RKGNKqdVo0T+PmsAxkZXN0aW5hdGlvbnOAEiEQVqurOHBYXwTQFdVa32AL"
			  + "xwVub25jZSAIAAOMrN5jtQAGb3duZXJzgFOBUQZwdWJsaWNAIQN4Wpwln96ZkeRPovsLVlnypXgawzkH"
			  + "bi2/73BSjkrfaApzZXJpYWxpemVyIAgAAAAAIJ3vOwd2ZXJzaW9uIAgAAAAAAAAAZAhxdWFudGl0eSAI"
			  + "AABa8xB6QAAKc2VyaWFsaXplciAIAAAAAGo7JYcHdmVyc2lvbiAIAAAAAAAAAGQNZGF0YVBhcnRpY2xl"
			  + "c4BGgUQFYnl0ZXNAFVJhZGl4Li4uLkp1c3QgSW1hZ2luZQpzZXJpYWxpemVyIAgAAAAAHDz8MAd2ZXJz"
			  + "aW9uIAgAAAAAAAAAZAxkZXN0aW5hdGlvbnOAEiEQVqurOHBYXwTQFdVa32ALxwpzZXJpYWxpemVyIAgA"
			  + "AAAAAB7RUQpzaWduYXR1cmVzgZQgNTZhYmFiMzg3MDU4NWYwNGQwMTVkNTVhZGY2MDBiYzeBcQFyQCEA"
			  + "zQ58YI4mrgJcYm1OHwILzh5d8q6nlyJw3PyacmUoG70Bc0AhAIy8PTdSKvuxMXgoFtWLSivkK3yUsoiZ"
			  + "pHW07PbbzROtCnNlcmlhbGl6ZXIgCP/////mFaiYB3ZlcnNpb24gCAAAAAAAAABkDnRlbXBvcmFsX3By"
			  + "b29mgaHDB2F0b21faWQhEFQJLERUJ06oUwTzRyTYxgUKc2VyaWFsaXplciAIAAAAAHGOn0IHdmVyc2lv"
			  + "biAIAAAAAAAAAGQIdmVydGljZXOAoXaBoXMFY2xvY2sgCAAAAAAAAAAACmNvbW1pdG1lbnQiIAAAAAAA"
			  + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABW93bmVygVEGcHVibGljQCECIOL2ztjYQ4hLsm3tYnT0"
			  + "p6ak8wgjeFvcd4FX8xoD+qYKc2VyaWFsaXplciAIAAAAACCd7zsHdmVyc2lvbiAIAAAAAAAAAGQIcHJl"
			  + "dmlvdXMhEAAAAAAAAAAAAAAAAAAAAAAKc2VyaWFsaXplciAI/////8nMm0YJc2lnbmF0dXJlgXABckAg"
			  + "G/L9YMQ9nlm2NWjyXsBzQuQUiIRnNP7py4NE8nKCq9MBc0AhAOOgneCD4w43b4UgxXUxJIRoPZU2hN5v"
			  + "zp+XF0I+amcICnNlcmlhbGl6ZXIgCP/////mFaiYB3ZlcnNpb24gCAAAAAAAAABkCnRpbWVzdGFtcHOB"
			  + "EgdkZWZhdWx0IAgAAAFlzndp0wd2ZXJzaW9uIAgAAAAAAAAAZAd2ZXJzaW9uIAgAAAAAAAAAZAVtYWdp"
			  + "YyAIAAAAAAPNgAIEbmFtZUEMUmFkaXggRGV2bmV0BHBvcnQgCAAAAAAAAHUwCnNlcmlhbGl6ZXIgCAAA"
			  + "AAAdWDpFC3NpZ25hdHVyZS5yQCEA3/TyS5Brw0DKNxS7a9SkStwWjosInatSPy/PSN8x//ULc2lnbmF0"
			  + "dXJlLnNAIQC1j28Pn6k4PVSxzLsFUla4O0M8gzX40qzDtHCrh6vWUgl0aW1lc3RhbXAgCAAAAVqHKpgA"
			  + "BHR5cGUgCAAAAAAAAAACB3ZlcnNpb24gCAAAAAAAAABk"
			);
		JsonElement jsonElement = dson.parse(originalDson);
		RadixUniverseConfig universeFromDson = RadixJson.getGson().fromJson(jsonElement, RadixUniverseConfig.class);
		assertEquals(63799298, universeFromDson.getMagic());
		assertEquals(3, universeFromDson.getGenesis().size());

		byte[] roundTrip = dson.toDson(universeFromDson);
		assertArrayEquals(originalDson, roundTrip);
	}
}
