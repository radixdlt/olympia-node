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
			ECKeyPair key = new ECKeyPair();
			ShardSpace shardSpace = new ShardSpace(key.getUID().getShard(), maxChunkRange);
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
