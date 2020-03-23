/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.utils;

public final class RuntimeUtils {

	// Taken from BitcoinJ implementation
	// https://github.com/bitcoinj/bitcoinj/blob/3cb1f6c6c589f84fe6e1fb56bf26d94cccc85429/core/src/main/java/org/bitcoinj/core/Utils.java#L573
	private static int isAndroid = -1;

	public static boolean isAndroidRuntime() {
		if (isAndroid == -1) {
			final String runtime = System.getProperty("java.runtime.name");
			isAndroid = (runtime != null && runtime.equals("Android Runtime")) ? 1 : 0;
		}
		return isAndroid == 1;
	}

	private RuntimeUtils() {
		// cannot construct
	}
}
