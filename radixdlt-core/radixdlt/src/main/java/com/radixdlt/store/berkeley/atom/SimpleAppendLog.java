/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.store.berkeley.atom;

import static java.nio.ByteBuffer.allocate;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import com.radixdlt.utils.Pair;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Implementation of simple append-only log */
public class SimpleAppendLog implements AppendLog {
  private static final Logger logger = LogManager.getLogger();
  private final FileChannel channel;
  private final ByteBuffer sizeBufferW;
  private final ByteBuffer sizeBufferR;

  private SimpleAppendLog(final FileChannel channel) {
    this.channel = channel;
    this.sizeBufferW = allocate(Integer.BYTES).order(ByteOrder.BIG_ENDIAN);
    this.sizeBufferR = allocate(Integer.BYTES).order(ByteOrder.BIG_ENDIAN);
  }

  static AppendLog open(String path) throws IOException {
    var channel = FileChannel.open(Path.of(path), EnumSet.of(READ, WRITE, CREATE));

    channel.position(channel.size());
    return new SimpleAppendLog(channel);
  }

  @Override
  public long write(byte[] data, long expectedOffset) throws IOException {
    synchronized (channel) {
      var position = channel.position();
      if (position > expectedOffset) {
        logger.warn(
            "Expected position to be "
                + expectedOffset
                + " but is "
                + position
                + ". Resetting position to "
                + expectedOffset);
        channel.position(expectedOffset);
      } else if (position < expectedOffset) {
        throw new IOException(
            "Expected position to be "
                + expectedOffset
                + " but is "
                + position
                + ". Cannot recover as there is missing data.");
      }

      sizeBufferW.clear().putInt(data.length).clear();
      checkedWrite(Integer.BYTES, sizeBufferW);
      checkedWrite(data.length, ByteBuffer.wrap(data));
      return (long) Integer.BYTES + data.length;
    }
  }

  @Override
  public Pair<byte[], Integer> readChunk(long offset) throws IOException {
    synchronized (channel) {
      checkedRead(offset, sizeBufferR.clear());
      var readLength = sizeBufferR.clear().getInt();
      return Pair.of(checkedRead(offset + Integer.BYTES, allocate(readLength)).array(), readLength);
    }
  }

  @Override
  public void flush() throws IOException {
    synchronized (channel) {
      channel.force(true);
    }
  }

  @Override
  public long position() {
    try {
      synchronized (channel) {
        return channel.position();
      }
    } catch (IOException e) {
      throw new IllegalStateException("Unable to obtain current position in log", e);
    }
  }

  @Override
  public void truncate(long position) {
    try {
      synchronized (channel) {
        channel.truncate(position);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Unable to truncate log", e);
    }
  }

  @Override
  public void close() {
    try {
      synchronized (channel) {
        channel.close();
      }
    } catch (IOException e) {
      throw new RuntimeException("Error while closing log", e);
    }
  }

  @Override
  public void forEach(BiConsumer<byte[], Long> chunkConsumer) {
    var offset = 0L;

    synchronized (channel) {
      var end = false;
      while (!end) {
        try {
          var chunk = readChunk(offset);
          chunkConsumer.accept(chunk.getFirst(), offset);
          offset += chunk.getSecond() + Integer.BYTES;
        } catch (IOException exception) {
          end = true;
        }
      }
    }
  }

  private void checkedWrite(int length, ByteBuffer buffer) throws IOException {
    int len = channel.write(buffer);

    if (len != length) {
      throw new IOException("Written less bytes than requested: " + len + " vs " + length);
    }
  }

  private ByteBuffer checkedRead(long offset, ByteBuffer buffer) throws IOException {
    int len = channel.read(buffer.clear(), offset);

    if (len != buffer.capacity()) {
      // Force flush and try again
      channel.force(true);
      len = channel.read(buffer.clear(), offset);
    }

    if (len != buffer.capacity()) {
      throw new IOException(
          "Got less bytes than requested: "
              + len
              + " vs "
              + buffer.capacity()
              + " at "
              + offset
              + ", size "
              + channel.size());
    }
    return buffer;
  }
}
