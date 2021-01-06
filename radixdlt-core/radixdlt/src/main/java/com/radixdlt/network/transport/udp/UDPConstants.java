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

package com.radixdlt.network.transport.udp;

/**
 * Various constants used by the UDP transport.
 */
public class UDPConstants {
	private UDPConstants() {
		throw new IllegalStateException("Can't construct");
	}

	/**
	 * The UDP transport name.
	 */
	public static final String NAME = "UDP";

	/**
	 * The "host" property for the UDP transport.
	 * Can be an IPv4/IPv6 raw address or host name.
	 */
	public static final String METADATA_HOST = "host";
	/**
	 * The "port" property for the UDP transport.
	 */
	public static final String METADATA_PORT = "port";

	/**
	 * Maximum packet size that we will attempt to send.
	 * Note that we have given ourselves some headroom here.
	 */
	static final int MAX_PACKET_LENGTH = 65_409;
}
