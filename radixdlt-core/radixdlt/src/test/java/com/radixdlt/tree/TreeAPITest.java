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

package com.radixdlt.tree;

import static org.junit.Assert.assertArrayEquals;

import com.radixdlt.tree.hash.Keccak256;
import com.radixdlt.tree.serialization.rlp.RLPSerializer;
import com.radixdlt.tree.storage.CachedPMTStorage;
import com.radixdlt.tree.storage.InMemoryPMTStorage;
import com.radixdlt.tree.storage.PMTCache;
import com.radixdlt.utils.Bytes;
import java.nio.charset.StandardCharsets;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

public class TreeAPITest {

  public static final int CACHE_MAXIMUM_SIZE = 1_000;

  @Test
  public void simpleAddGet() {

    var storage = new InMemoryPMTStorage();
    var tree = new PMT(storage);
    var sub1 =
        "300126433882d547b3fbb20ca1935879e03a4f75b474546ccf39b4cd03edbe1600000000".getBytes();
    var val1 = "1000000000".getBytes();

    tree = tree.add(sub1, val1);

    var val1back = tree.get(sub1);
    assertArrayEquals(val1, val1back);
  }

  @Test
  public void when_tree_contains_extension_nodes__then_values_can_be_added_and_retrieved() {
    var storage = new CachedPMTStorage(new InMemoryPMTStorage(), new PMTCache(CACHE_MAXIMUM_SIZE));
    var tree = new PMT(storage, new Keccak256(), new RLPSerializer());

    String verbKey = "646f";
    String verbValue = "verb";
    tree = tree.add(Hex.decode(verbKey), verbValue.getBytes(StandardCharsets.UTF_8));

    assertArrayEquals(tree.get(Hex.decode(verbKey)), verbValue.getBytes(StandardCharsets.UTF_8));

    String puppyKey = "646f67";
    String puppyValue = "puppy";
    tree = tree.add(Hex.decode(puppyKey), puppyValue.getBytes(StandardCharsets.UTF_8));

    assertArrayEquals(tree.get(Hex.decode(puppyKey)), puppyValue.getBytes(StandardCharsets.UTF_8));

    String coinKey = "646f6765";
    String coinValue = "coin";
    tree = tree.add(Hex.decode(coinKey), coinValue.getBytes(StandardCharsets.UTF_8));

    assertArrayEquals(tree.get(Hex.decode(coinKey)), coinValue.getBytes(StandardCharsets.UTF_8));

    String stallionKey = "686f727365";
    String stallionValue = "stallion";
    tree = tree.add(Hex.decode(stallionKey), stallionValue.getBytes(StandardCharsets.UTF_8));

    assertArrayEquals(tree.get(Hex.decode(verbKey)), verbValue.getBytes(StandardCharsets.UTF_8));

    assertArrayEquals(tree.get(Hex.decode(puppyKey)), puppyValue.getBytes(StandardCharsets.UTF_8));

    assertArrayEquals(tree.get(Hex.decode(coinKey)), coinValue.getBytes(StandardCharsets.UTF_8));

    assertArrayEquals(
        tree.get(Hex.decode(stallionKey)), stallionValue.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void when_same_key_and_value_are_added_twice__then_root_hash_does_not_change() {
    var storage = new CachedPMTStorage(new InMemoryPMTStorage(), new PMTCache(CACHE_MAXIMUM_SIZE));
    var tree = new PMT(storage, new Keccak256(), new RLPSerializer());

    String verbKey = "646f";
    String verbValue = "verb";

    var treeBefore = tree.add(Hex.decode(verbKey), verbValue.getBytes(StandardCharsets.UTF_8));

    var treeAfter = treeBefore.add(Hex.decode(verbKey), verbValue.getBytes(StandardCharsets.UTF_8));

    assertArrayEquals(treeBefore.getRootHash(), treeAfter.getRootHash());

    assertArrayEquals(treeAfter.get(Hex.decode(verbKey)), verbValue.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void when_tree_is_empty_and_rlp_and_keccak256_are_used__then_correct_hash_is_returned() {
    var rlpKeccak256EmptyArray = "56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421";

    var storage = new CachedPMTStorage(new InMemoryPMTStorage(), new PMTCache(CACHE_MAXIMUM_SIZE));
    var tree = new PMT(storage, new Keccak256(), new RLPSerializer());

    assertArrayEquals(Bytes.fromHexString(rlpKeccak256EmptyArray), tree.getRootHash());
  }

  @Test
  public void when_new_tree_uses_same_db__then_current_root_should_be_used() {
    var storage = new CachedPMTStorage(new InMemoryPMTStorage(), new PMTCache(CACHE_MAXIMUM_SIZE));
    var tree = new PMT(storage, new Keccak256(), new RLPSerializer());

    String verbKey = "646f";
    String verbValue = "verb";
    tree =
        tree.add(Hex.decode(verbKey), verbValue.getBytes(StandardCharsets.UTF_8));

    var newTree = new PMT(storage, storage.read(tree.getRootHash()), new Keccak256(), new RLPSerializer());

    assertArrayEquals(
        "Tree does not have the right root hash", newTree.getRootHash(), tree.getRootHash());

    assertArrayEquals(tree.get(Hex.decode(verbKey)), verbValue.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void when_tree_is_empty__then_empty_array_should_be_returned() {
    var storage = new CachedPMTStorage(new InMemoryPMTStorage(), new PMTCache(CACHE_MAXIMUM_SIZE));
    var tree = new PMT(storage, new Keccak256(), new RLPSerializer());

    String verbKey = "646f";

    assertArrayEquals(
        "Get should return empty array when tree is empty",
        new byte[0],
        tree.get(verbKey.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  public void when_tree_does_not_contain_key__then_empty_array_should_be_returned() {
    var storage = new CachedPMTStorage(new InMemoryPMTStorage(), new PMTCache(CACHE_MAXIMUM_SIZE));
    var tree = new PMT(storage, new Keccak256(), new RLPSerializer());

    String verbKey = "646f";
    String verbValue = "verb";
    final var rootHash = tree.add(Hex.decode(verbKey), verbValue.getBytes(StandardCharsets.UTF_8));

    String nonExistingKey = "non_existing_key";

    assertArrayEquals(
        "Get should return empty array when key is not found",
        new byte[0],
        tree.get(nonExistingKey.getBytes(StandardCharsets.UTF_8)));
  }
}
