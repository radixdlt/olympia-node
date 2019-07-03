package org.radix.api.services;

import com.google.common.collect.EvictingQueue;
import com.radixdlt.atoms.AtomStatus;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.Map;
import org.radix.api.observable.AtomEventDto;
import org.radix.api.observable.AtomEventDto.AtomEventType;
import com.radixdlt.atomos.RadixAddress;
import org.radix.api.AtomQuery;
import org.radix.api.observable.AtomEventObserver;
import org.radix.api.observable.Disposable;
import org.radix.api.observable.ObservedAtomEvents;
import org.radix.api.observable.Observable;
import com.radixdlt.atoms.Atom;
import org.radix.atoms.AtomDiscoveryRequest;
import org.radix.atoms.AtomStore;
import org.radix.atoms.events.AtomDeletedEvent;
import org.radix.atoms.events.AtomEvent;
import org.radix.atoms.events.AtomExceptionEvent;
import org.radix.atoms.events.AtomStoreEvent;
import org.radix.atoms.events.AtomStoredEvent;
import org.radix.atoms.events.AtomUpdatedEvent;
import com.radixdlt.atoms.Particle;
import org.radix.atoms.particles.conflict.ParticleConflict;
import org.radix.atoms.particles.conflict.ParticleConflictStore;
import org.radix.atoms.sync.AtomSync;
import com.radixdlt.common.EUID;
import com.radixdlt.common.AID;
import org.radix.containers.BasicContainer;
import org.radix.discovery.DiscoveryCursor;
import org.radix.discovery.DiscoveryException;
import org.radix.discovery.DiscoveryRequest.Action;
import org.radix.events.Events;
import org.radix.exceptions.AtomAlreadyInProcessingException;
import org.radix.exceptions.AtomAlreadyStoredException;
import org.radix.modules.Modules;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import org.radix.shards.ShardSpace;
import org.radix.shards.Shards;
import org.radix.time.Timestamps;
import org.radix.universe.system.LocalSystem;

public class AtomsService {
	/**
	 * Some of these may block for a short while so keep a few.
	 * TODO: remove the blocking
	 */
	private final static ExecutorService executorService = Executors.newFixedThreadPool(
		8,
		new ThreadFactoryBuilder().setNameFormat("AtomsService-%d").build()
	);

	private final Set<AtomEventObserver> atomEventObservers = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private final ConcurrentHashMap<AID, List<AtomStatusListener>> singleAtomObservers = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<AID, List<SingleAtomListener>> deleteOnEventSingleAtomObservers = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<AtomEventDto.AtomEventType, Long> atomEventCount = new ConcurrentHashMap<>();

	private final Object lock = new Object();
	private final EvictingQueue<String> eventRingBuffer = EvictingQueue.create(64);
	private final AtomSync atomSync;

	public AtomsService(AtomSync atomSync) {
		this.atomSync = Objects.requireNonNull(atomSync);

		Events.getInstance().register(AtomEvent.class, (event) -> {
			executorService.submit(() -> {
				if (!(event instanceof AtomEvent)) {
					return;
				}

				final AtomEvent atomEvent = (AtomEvent) event;
				final Atom atom = atomEvent.getAtom();

				// TODO: Clean this up
				final String eventName;

				if (event instanceof AtomStoredEvent) {
					final AtomStoredEvent storedEvent = (AtomStoredEvent) atomEvent;
					eventName = "STORED";

					this.atomEventObservers.forEach(observer -> observer.tryNext(storedEvent));

					List<SingleAtomListener> subscribers = this.deleteOnEventSingleAtomObservers.remove(atom.getAID());
					if (subscribers != null) {
						Iterator<SingleAtomListener> i = subscribers.iterator();
						if (i.hasNext()) {
							i.next().onStored(true);
							while (i.hasNext()) {
								i.next().onStored(false);
							}
						}
					}

					for (AtomStatusListener atomStatusListener : this.singleAtomObservers.getOrDefault(atom.getAID(), Collections.emptyList())) {
						atomStatusListener.onStored();
					}

					this.atomEventCount.merge(AtomEventType.STORE, 1L, Long::sum);
				} else if (event instanceof AtomDeletedEvent) {
					final AtomDeletedEvent deletedEvent = (AtomDeletedEvent) atomEvent;
					eventName = "DELETED";

					this.atomEventObservers.forEach(observer -> observer.tryNext(deletedEvent));
					for (AtomStatusListener atomStatusListener : this.singleAtomObservers.getOrDefault(atom.getAID(), Collections.emptyList())) {
						atomStatusListener.onDeleted();
					}

					this.atomEventCount.merge(AtomEventType.DELETE, 1L, Long::sum);
				} else if (event instanceof AtomUpdatedEvent) {
					eventName = "UPDATED";
				} else if (event instanceof AtomStoreEvent) {
					eventName = "STORE";
				} else {
					eventName = "UNKNOWN";
				}

				synchronized (lock) {
					eventRingBuffer.add(System.currentTimeMillis() + " " + eventName + " " + atomEvent.getAtom().getAID());
				}
			});
		});

		Events.getInstance().register(AtomExceptionEvent.class, event -> {
			executorService.submit(() -> {
				final AtomExceptionEvent exceptionEvent = (AtomExceptionEvent) event;
				final Atom atom = exceptionEvent.getAtom();
				synchronized (lock) {
					eventRingBuffer.add(
						System.currentTimeMillis() + " EXCEPTION " + exceptionEvent.getAtom().getAID()
							+ " " + exceptionEvent.getException().getClass().getName());
				}

				List<SingleAtomListener> subscribers = this.deleteOnEventSingleAtomObservers.remove(atom.getAID());
				if (subscribers != null) {
					Throwable exception = exceptionEvent.getException();
					subscribers.forEach(subscriber -> subscriber.onError(exception));
				}

				for (AtomStatusListener singleAtomListener : this.singleAtomObservers.getOrDefault(atom.getAID(), Collections.emptyList())) {
					Throwable exception = exceptionEvent.getException();
					singleAtomListener.onError(exception);
				}
			});
		});
	}

	/**
	 * Get a list of the most recent events
	 * @return list of event strings
	 */
	public List<String> getEvents() {
		synchronized (lock) {
			return new ArrayList<>(eventRingBuffer);
		}
	}

	public Map<AtomEventType, Long> getAtomEventCount() {
		return Collections.unmodifiableMap(atomEventCount);
	}

	public AtomStatus submitAtom(Atom atom) {
		ShardSpace shardsSupported = LocalSystem.getInstance().getShards();
		if (!shardsSupported.intersects(atom.getShards())) {
			throw new AtomShardsNotServedException(atom.getShards(), shardsSupported);
		}

		try {
			atomSync.store(atom);
		} catch (AtomAlreadyInProcessingException e) {
			return AtomStatus.PENDING_CM_VERIFICATION;
		} catch (AtomAlreadyStoredException e) {
			return AtomStatus.STORED;
		}

		return AtomStatus.PENDING_CM_VERIFICATION;
	}

	public Disposable subscribeAtomStatusNotifications(AID aid, AtomStatusListener subscriber) {
		this.singleAtomObservers.compute(aid, (hid, oldSubscribers) -> {
			List<AtomStatusListener> subscribers = oldSubscribers == null ? new ArrayList<>() : oldSubscribers;
			subscribers.add(subscriber);
			return subscribers;
		});

		return () -> this.singleAtomObservers.get(aid).remove(subscriber);
	}

	public void subscribeAtom(AID aid, SingleAtomListener subscriber) {
		this.deleteOnEventSingleAtomObservers.compute(aid, (hid, oldSubscribers) -> {
			List<SingleAtomListener> subscribers = oldSubscribers == null ? new ArrayList<>() : oldSubscribers;
			subscribers.add(subscriber);
			return subscribers;
		});
	}

	private static final class AtomShardsNotServedException extends RuntimeException {
		private AtomShardsNotServedException(Set<Long> atomShards, ShardSpace supportedShards) {
			super("Not a suitable submission peer: "
				+ "atomShards(" + atomShards+ ") shardsSupported(" + supportedShards+ ")");
		}
	}

	public Observable<ObservedAtomEvents> getAtomEvents(AtomQuery atomQuery) {
		return observer -> {
			final AtomEventObserver atomEventObserver = new AtomEventObserver(atomQuery, observer, executorService);
			atomEventObserver.start();
			this.atomEventObservers.add(atomEventObserver);

			return () -> {
				this.atomEventObservers.remove(atomEventObserver);
				atomEventObserver.cancel();
			};
		};
	}

	public long getWaitingCount() {
		return this.atomEventObservers.stream().map(AtomEventObserver::isDone).filter(done -> !done).count();
	}

	public JSONObject getAtoms(
		String particle, //
		String aid, //
		String uid, //
		String address, //
		String action, //
		String destination, //
		String index, //
		String time, //
		String limit) throws DiscoveryException, JSONException {

		JSONObject result = new JSONObject();

		Class<? extends Particle> particleClazz = null;

		if (particle != null) {
			particleClazz = (Class<? extends Particle>) Modules.get(Serialization.class).getClassForId(particle);
		}

		Action requestAction = Action.DISCOVER_AND_DELIVER;
		if (action != null && action.toUpperCase().equals("DISCOVER")) {
			requestAction = Action.DISCOVER;
		}

		AtomDiscoveryRequest request;

		if (particleClazz != null) {
			request = new AtomDiscoveryRequest(particleClazz, requestAction);
		} else {
			request = new AtomDiscoveryRequest(requestAction);
		}

		if (uid != null) {
			request.setUID(EUID.valueOf(uid));
		}

		if (aid != null) {
			request.setAID(AID.from(aid));
		}

		if (address != null) {
			request.setDestination(RadixAddress.from(address).getUID());
		}

		if (destination != null) {
			request.setDestination(EUID.valueOf(destination));
		}

		request.setCursor(new DiscoveryCursor(index == null ? 0 : Long.parseLong(index)));

		if (time != null) {
			StringTokenizer timestamps = new StringTokenizer(time, ",");
			if (timestamps.hasMoreTokens()) {
				request.setTimestamp(Timestamps.FROM, Integer.parseInt(timestamps.nextToken()) * 1000L);
			}
			if (timestamps.hasMoreTokens()) {
				request.setTimestamp(Timestamps.TO, Integer.parseInt(timestamps.nextToken()) * 1000L);
			}
		}

		if (limit != null) {
			request.setLimit(Short.parseShort(limit));
		} else {
			request.setLimit((short) 50);
		}

		Modules.get(AtomStore.class).discovery(request);

		if (requestAction.equals(Action.DISCOVER)) {
			for (AID id : request.getInventory()) {
				result.append("ids", id.toString());
			}
		} else {
			JSONArray array = new JSONArray();
			for (BasicContainer container : request.getDelivered()) {
				array.put(Modules.get(Serialization.class).toJsonObject(container, Output.API));
			}
			result.put("data", array);
		}

		// result.put("links", APICommons.createLinks(url, request));

		return result;
	}

	public JSONObject getAtomsByShardRange(String from, String to) throws IOException, JSONException
	{
		JSONObject result = new JSONObject();

		Collection<AID> shardHIDS = Modules.get(AtomStore.class).getByShardRange(Shards.fromGroup(Integer.parseInt(from), (1 << 20)).getLow(),
																				  Shards.fromGroup(Integer.parseInt(to == null ? from : to), (1 << 20)).getHigh());

		JSONArray array = new JSONArray();
		for (AID aid : shardHIDS) {
			array.put(aid.toString());
		}
		result.put("hids", array);
		return result;
	}

	public JSONObject getConflict(String uid) throws JSONException, IOException
	{
		ParticleConflict conflict = Modules.get(ParticleConflictStore.class).getConflict(EUID.valueOf(uid));

		if (conflict != null)
			return Modules.get(Serialization.class).toJsonObject(conflict, Output.API);

		return null;
	}
}
