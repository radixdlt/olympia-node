/* Copyright 2019 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
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

package org.bitcoinj.crypto;

import com.google.common.base.Splitter;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * HD Key derivation path. {@code HDPath} can be used to represent a full path or a relative path.
 * The {@code hasPrivateKey} {@code boolean} is used for rendering to {@code String} but (at
 * present) not much else. It defaults to {@code false} which is the preferred setting for a
 * relative path.
 *
 * <p>{@code HDPath} is immutable and uses the {@code Collections.UnmodifiableList} type internally.
 *
 * <p>It implements {@code java.util.List<ChildNumber>} to ease migration from the previous Guava
 * {@code ImmutableList<ChildNumber>}. It should be a minor breaking change to replace {@code
 * ImmutableList<ChildNumber>} with {@code List<ChildNumber>} where necessary in your code. Although
 * it is recommended to use the {@code HDPath} type for clarity and for access to {@code
 * HDPath}-specific functionality.
 *
 * <p>Take note of the overloaded factory methods {@link HDPath#m()}. These can be used to very
 * concisely create HDPath objects (especially when statically imported.)
 */
public class HDPath extends AbstractList<ChildNumber> {
  private static final char PREFIX_PRIVATE = 'm';
  private static final char PREFIX_PUBLIC = 'M';
  private static final char SEPARATOR = '/';
  private static final Splitter SEPARATOR_SPLITTER = Splitter.on(SEPARATOR).trimResults();
  protected final boolean hasPrivateKey;
  protected final List<ChildNumber> unmodifiableList;

  /**
   * Constructs a path for a public or private key.
   *
   * @param hasPrivateKey Whether it is a path to a private key or not
   * @param list List of children in the path
   */
  public HDPath(boolean hasPrivateKey, List<ChildNumber> list) {
    this.hasPrivateKey = hasPrivateKey;
    this.unmodifiableList = Collections.unmodifiableList(list);
  }

  /**
   * Constructs a path for a public key.
   *
   * @param list List of children in the path
   */
  public HDPath(List<ChildNumber> list) {
    this(false, list);
  }

  /**
   * Returns a path for a public or private key.
   *
   * @param hasPrivateKey Whether it is a path to a private key or not
   * @param list List of children in the path
   */
  private static HDPath of(boolean hasPrivateKey, List<ChildNumber> list) {
    return new HDPath(hasPrivateKey, list);
  }

  /**
   * Returns a path for a private key.
   *
   * @param list List of children in the path
   */
  public static HDPath m(List<ChildNumber> list) {
    return HDPath.of(true, list);
  }

  /** Returns an empty path for a private key. */
  public static HDPath m() {
    return HDPath.m(Collections.<ChildNumber>emptyList());
  }

  /**
   * Returns a path for a private key.
   *
   * @param childNumber Single child in path
   */
  public static HDPath m(ChildNumber childNumber) {
    return HDPath.m(Collections.singletonList(childNumber));
  }

  /**
   * Returns a path for a private key.
   *
   * @param children Children in the path
   */
  public static HDPath m(ChildNumber... children) {
    return HDPath.m(Arrays.asList(children));
  }

  /**
   * Create an HDPath from a path string. The path string is a human-friendly representation of the
   * deterministic path. For example:
   *
   * <p>"44H / 0H / 0H / 1 / 1"
   *
   * <p>Where a letter "H" means hardened key. Spaces are ignored.
   */
  public static HDPath parsePath(@Nonnull String path) {
    List<String> parsedNodes = new LinkedList<>(SEPARATOR_SPLITTER.splitToList(path));
    boolean hasPrivateKey = false;
    if (!parsedNodes.isEmpty()) {
      final String firstNode = parsedNodes.get(0);
      if (firstNode.equals(Character.toString(PREFIX_PRIVATE))) {
        hasPrivateKey = true;
      }
      if (hasPrivateKey || firstNode.equals(Character.toString(PREFIX_PUBLIC))) {
        parsedNodes.remove(0);
      }
    }
    List<ChildNumber> nodes = new ArrayList<>(parsedNodes.size());

    for (String n : parsedNodes) {
      if (n.isEmpty()) {
        continue;
      }
      boolean isHard = n.endsWith("H");
      if (isHard) {
        n = n.substring(0, n.length() - 1).trim();
      }
      int nodeNumber = Integer.parseInt(n);
      nodes.add(new ChildNumber(nodeNumber, isHard));
    }

    return new HDPath(hasPrivateKey, nodes);
  }

  /**
   * Is this a path to a private key?
   *
   * @return true if yes, false if no or a partial path
   */
  public boolean hasPrivateKey() {
    return hasPrivateKey;
  }

  /**
   * Extend the path by appending additional ChildNumber objects.
   *
   * @param child1 the first child to append
   * @param children zero or more additional children to append
   * @return A new immutable path
   */
  public HDPath extend(ChildNumber child1, ChildNumber... children) {
    List<ChildNumber> mutable = new ArrayList<>(this.unmodifiableList); // Mutable copy
    mutable.add(child1);
    mutable.addAll(Arrays.asList(children));
    return new HDPath(this.hasPrivateKey, mutable);
  }

  /**
   * Extend the path by appending a relative path.
   *
   * @param path2 the relative path to append
   * @return A new immutable path
   */
  public HDPath extend(HDPath path2) {
    List<ChildNumber> mutable = new ArrayList<>(this.unmodifiableList); // Mutable copy
    mutable.addAll(path2);
    return new HDPath(this.hasPrivateKey, mutable);
  }

  @Override
  public ChildNumber get(int index) {
    return unmodifiableList.get(index);
  }

  @Override
  public int size() {
    return unmodifiableList.size();
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    b.append(hasPrivateKey ? HDPath.PREFIX_PRIVATE : HDPath.PREFIX_PUBLIC);
    for (ChildNumber segment : unmodifiableList) {
      b.append(HDPath.SEPARATOR);
      b.append(segment.toString());
    }
    return b.toString();
  }
}
