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

package org.radix;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.Set;
import com.radixdlt.crypto.ECKeyPair;
import org.radix.shards.ShardSpace;


public final class GenerateNodeKey
{
	public static void main(String[] args) throws Exception
	{
		long targetShard = args.length > 0 ? Long.parseLong(args[0]) : 0;
		long maxChunkRange = args.length > 1 ? Long.parseLong(args[1]) : ShardSpace.SHARD_CHUNK_RANGE / 10;
		File file = args.length > 2 ? new File(args[2]) : new File("node.key");

		while (true) {
			ECKeyPair key = ECKeyPair.generateNew();
			ShardSpace shardSpace = new ShardSpace(key.euid().getShard(), maxChunkRange);
			if (shardSpace.intersects(targetShard)) {
				System.out.println("targetShard: " + targetShard);
				System.out.println("maxChunkRange: " + maxChunkRange);
				System.out.println("Creating " + file + " for shard space: " + shardSpace);
				try (FileOutputStream io = new FileOutputStream(file)) {
					try {
						Set<PosixFilePermission> perms = EnumSet.of(
							PosixFilePermission.OWNER_READ,
							PosixFilePermission.OWNER_WRITE
						);
						Files.setPosixFilePermissions(file.toPath(), perms);
					} catch (UnsupportedOperationException ignoredException) {
						// probably windows
					}
					io.write(key.getPrivateKey());
				}
				break;
			}
		}
	}
}
