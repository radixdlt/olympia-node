package com.radixdlt.client.core.network;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.ledger.selector.CompatibleApiVersionFilter;
import com.radixdlt.client.core.ledger.selector.ConnectionAliveFilter;
import com.radixdlt.client.core.ledger.selector.MatchingUniverseFilter;
import com.radixdlt.client.core.ledger.selector.RadixPeerFilter;
import com.radixdlt.client.core.ledger.selector.RadixPeerSelector;
import com.radixdlt.client.core.ledger.selector.RandomSelector;
import com.radixdlt.client.core.ledger.selector.ShardFilter;
import com.radixdlt.client.core.network.epics.RadixNodesEpic;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.Map.Entry;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Given a network, a selector and filters, yields the {@link RadixJsonRpcClient} to use
 */
public class RadixClientSupplier {
	/**
	 * The network of peers available to connect to
	 */
	private final RadixNodesEpic network;

	/**
	 * The selector to use to decide between a list of viable peers
	 */
	private final RadixPeerSelector selector;

	/**
	 * The filters to test whether a peer is desirable
	 */
	private final List<RadixPeerFilter> filters;
	private final int targetDesirablePeerCount = 1;
	private final Logger logger = LoggerFactory.getLogger(RadixClientSupplier.class);

	/**
	 * Create a client selector from a certain network with the default filters and the a randomized selector
	 *
	 * @param network The network
	 */
	public RadixClientSupplier(RadixNodesEpic network) {
		this(network, new RandomSelector(), Arrays.asList(
				new ConnectionAliveFilter(),
				new CompatibleApiVersionFilter(RadixJsonRpcClient.API_VERSION)));
	}

	/**
	 * Create a client selector from a certain network with a matching universe filter, the default filters and a randomized selector
	 *
	 * @param network The network
	 * @param config  The universe config for the matching universe filter
	 */
	public RadixClientSupplier(RadixNodesEpic network, RadixUniverseConfig config) {
		this(network, new RandomSelector(), Arrays.asList(
				new ConnectionAliveFilter(),
				new CompatibleApiVersionFilter(RadixJsonRpcClient.API_VERSION),
				new MatchingUniverseFilter(config)));
	}

	/**
	 * Create a client selector from a certain network with a certain selector and filters
	 *
	 * @param network  The network
	 * @param selector The selector used to select a peer from the desirable peer list
	 * @param filters  The filters used to test the desirability of peers
	 */
	public RadixClientSupplier(RadixNodesEpic network, RadixPeerSelector selector, RadixPeerFilter... filters) {
		this(network, selector, Arrays.asList(filters));
	}

	/**
	 * Create a client selector from a certain network with a certain selector and filters
	 *
	 * @param network  The network
	 * @param selector The selector used to select a peer from the desirable peer list
	 * @param filters  The filters used to test the desirability of peers
	 */
	public RadixClientSupplier(RadixNodesEpic network, RadixPeerSelector selector, List<RadixPeerFilter> filters) {
		Objects.requireNonNull(network, "network is required");
		Objects.requireNonNull(selector, "selector is required");
		Objects.requireNonNull(filters, "filters is required");

		this.network = network;
		this.selector = selector;
		this.filters = Collections.unmodifiableList(new ArrayList<>(filters));
	}

	/**
	 * Returns a cold single of the first peer found which supports
	 * a set short shards which intersects with a given shard
	 *
	 * @return a cold single of the first matching Radix client
	 */
	public Single<RadixNode> getRadixClient() {
		return this.getRadixClients(this.selector, this.filters).firstOrError();
	}

	/**
	 * Returns a cold single of the first peer found which supports
	 * a set short shards which intersects with a given shard
	 *
	 * @param shard a shards to find an intersection with
	 * @return a cold single of the first matching Radix client
	 */
	public Single<RadixNode> getRadixClient(Long shard) {
		return this.getRadixClient(Collections.singleton(shard));
	}

	/**
	 * Returns a cold single of the first peer found which supports
	 * a set short shards which intersects with a given set of shards.
	 *
	 * @param shards set of shards to find an intersection with
	 * @return a cold single of the first matching Radix client
	 */
	public Single<RadixNode> getRadixClient(Set<Long> shards) {
		if (shards.isEmpty()) {
			throw new IllegalArgumentException("Shards cannot be empty to obtain a radixClient.");
		}

		List<RadixPeerFilter> expandedFilters = Collections.emptyList();//new ArrayList<>(this.filters);
		//expandedFilters.add(new ShardFilter(shards));

		return this.getRadixClients(this.selector, expandedFilters).firstOrError();
	}

	/**
	 * Returns a cold observable of viable peers found according
	 * to the selector and filters configured.
	 *
	 * @return A cold observable of clients
	 */
	public Observable<RadixNode> getRadixClients() {
		return this.getRadixClients(this.selector, this.filters);
	}

	/**
	 * Returns a cold observable of the first peer found which supports
	 * a set short shards which intersects with a given set of shards.
	 *
	 * @param shards set of shards to find an intersection with
	 * @return a cold observable of the first matching Radix client
	 */
	public Observable<RadixNode> getRadixClients(Set<Long> shards) {
		if (shards.isEmpty()) {
			throw new IllegalArgumentException("Shards cannot be empty to obtain a radixClient.");
		}

		List<RadixPeerFilter> expandedFilters = new ArrayList<>(this.filters);
		expandedFilters.add(new ShardFilter(shards));

		return this.getRadixClients(this.selector, expandedFilters);
	}

	private Observable<RadixNode> getRadixClients(RadixPeerSelector selector, List<RadixPeerFilter> filters) {
		return this.network.getNetworkState()
			.doOnNext(this::manageConnections)
			.map(state -> this.collectDesirablePeers(filters, state))
			.filter(viablePeerList -> !viablePeerList.isEmpty())
			.map(selector::apply);
	}

	private void manageConnections(RadixNetworkState state) {
		final Map<RadixNodeStatus,List<RadixNode>> statusMap = Arrays.stream(RadixNodeStatus.values())
			.collect(Collectors.toMap(
				Function.identity(),
				s -> state.getPeers().entrySet().stream().filter(e -> e.getValue().equals(s)).map(Entry::getKey).collect(Collectors.toList())
			));
		final long activeNodeCount = statusMap.get(RadixNodeStatus.CONNECTED).size()
			+ statusMap.get(RadixNodeStatus.CONNECTING).size();
		if (activeNodeCount < this.targetDesirablePeerCount) {
			this.logger.info(
				String.format("Requesting more peer connections, want %d but have %d desirable peers",
				this.targetDesirablePeerCount,
				activeNodeCount)
			);

			List<RadixNode> disconnectedPeers = statusMap.get(RadixNodeStatus.DISCONNECTED);
			if (disconnectedPeers.isEmpty()) {
				this.logger.info("Could not connect to new peer, don't have any.");
			} else {
				//network.connect(disconnectedPeers.get(0));
			}
		}
	}

	private List<RadixNode> collectDesirablePeers(List<RadixPeerFilter> filters, RadixNetworkState state) {
		return state.getPeers().entrySet().stream()
				.filter(entry -> entry.getValue().equals(RadixNodeStatus.CONNECTED))
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());
	}
}
