package org.radix.api.http;

import java.io.IOException;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.radixdlt.mock.MockAccessor;
import com.radixdlt.mock.MockApplication;
import com.radixdlt.tempo.AtomStoreView;
import com.radixdlt.tempo.AtomSyncView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.radix.api.AtomSchemas;
import org.radix.api.jsonrpc.RadixJsonRpcPeer;
import org.radix.api.jsonrpc.RadixJsonRpcServer;
import org.radix.api.services.AdminService;
import org.radix.api.services.AtomsService;
import org.radix.api.services.GraphService;
import org.radix.api.services.InternalService;
import org.radix.api.services.NetworkService;
import org.radix.api.services.TestService;
import org.radix.api.services.UniverseService;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.properties.RuntimeProperties;
import org.radix.shards.ShardSpace;

import com.radixdlt.serialization.Serialization;
import org.radix.time.Time;
import org.radix.time.RTP.RTPService;
import org.radix.time.RTP.RTPTimestamp;
import com.radixdlt.universe.Universe;
import org.radix.universe.system.LocalSystem;

import com.stijndewitt.undertow.cors.AllowAll;
import com.stijndewitt.undertow.cors.Filter;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import io.undertow.websockets.core.WebSocketChannel;

/**
 * TODO: Document me!
 */
public final class RadixHttpServer {
    public static final int DEFAULT_PORT = 8080;
    public static final String CONTENT_TYPE_JSON = "application/json";

    private static final Logger logger = Logging.getLogger("api");

    private final ConcurrentHashMap<RadixJsonRpcPeer, WebSocketChannel> peers = new ConcurrentHashMap<>();

	private final AtomsService atomsService = new AtomsService(Modules.get(AtomSyncView.class));

    private final RadixJsonRpcServer jsonRpcServer = new RadixJsonRpcServer(
		Modules.get(Serialization.class),
		Modules.get(AtomStoreView.class),
		Modules.get(AtomSyncView.class),
		atomsService,
        AtomSchemas.get()
    );

    private Undertow server;

    /**
     * Get the set of currently connected peers
     *
     * @return The currently connected peers
     */
    public final Set<RadixJsonRpcPeer> getPeers() {
        return Collections.unmodifiableSet(peers.keySet());
    }

    public final void start() {
        RoutingHandler handler = Handlers.routing(true); // add path params to query params with this flag

        // add all REST routes
        addRestRoutesTo(handler);

        // handle POST requests
        addPostRoutesTo(handler);

        // handle websocket requests (which also uses GET method)
        handler.add(
        	Methods.GET,
			"/rpc",
			Handlers.websocket(new RadixHttpWebsocketHandler( this, jsonRpcServer, peers, atomsService))
		);

        // add appropriate error handlers for meaningful error messages (undertow is silent by default)
        handler.setFallbackHandler(exchange ->
        {
            exchange.setStatusCode(StatusCodes.NOT_FOUND);
            exchange.getResponseSender().send("No matching path found for " + exchange.getRequestMethod() + " " + exchange.getRequestPath());
        });
        handler.setInvalidMethodHandler(exchange -> {
            exchange.setStatusCode(StatusCodes.NOT_ACCEPTABLE);
            exchange.getResponseSender().send("Invalid method, path exists for " + exchange.getRequestMethod() + " " + exchange.getRequestPath());
        });

        // if we are in a development universe, add the dev only routes (e.g. for spamathons)
        if (Modules.get(Universe.class).isDevelopment()) {
            addDevelopmentOnlyRoutesTo(handler);
        }
        if (Modules.get(Universe.class).isDevelopment() || Modules.get(Universe.class).isTest()) {
        	addTestRoutesTo(handler);
        }

        Integer port = Modules.get(RuntimeProperties.class).get("cp.port", DEFAULT_PORT);
        Filter corsFilter = new Filter(handler);
        corsFilter.setPolicyClass(AllowAll.class.getName());
        corsFilter.setUrlPattern("^.*$");
        server = Undertow.builder()
                .addHttpListener(port, "0.0.0.0")
                .setHandler(corsFilter)
                .build();
        server.start();
    }

    public final void stop() {
        server.stop();
    }

    private void addDevelopmentOnlyRoutesTo(RoutingHandler handler) {
    	addGetRoute("/api/internal/mock/spam", exchange -> {
			if (Modules.isAvailable(MockAccessor.class)) {
				String atomCountStr = getParameter(exchange, "atoms").orElse("1");
				int atomCount = Integer.parseUnsignedInt(atomCountStr);
				respond("Spamming " + atomCount + " random atom(s)", exchange);
				Modules.get(MockAccessor.class).spam(atomCount);
			} else {
				respond("Mock application is unavailable", exchange);
			}
	    }, handler);

        addGetRoute("/api/internal/spamathon", exchange -> {
            String iterations = getParameter(exchange, "iterations").orElse(null);
            String batching = getParameter(exchange, "batching").orElse(null);
            String rate = getParameter(exchange, "rate").orElse(null);

            respond(InternalService.getInstance().spamathon(iterations, batching, rate), exchange);
        }, handler);

		addGetRoute("/api/internal/bulkpreparemessages", exchange -> {
			String atomCount = getParameter(exchange, "atoms").orElse("100");
			respond(InternalService.getInstance().prepareMessages(atomCount), exchange);
		}, handler);
		addGetRoute("/api/internal/bulkpreparetransfers", exchange -> {
			String atomCount = getParameter(exchange, "atoms").orElse("100");
			respond(InternalService.getInstance().prepareTransfers(atomCount), exchange);
		}, handler);
		addGetRoute("/api/internal/bulkstore", exchange -> {
			respond(InternalService.getInstance().bulkstore(), exchange);
		}, handler);
		addGetRoute("/api/internal/ping", exchange -> {
			respond(InternalService.getInstance().ping(), exchange);
		}, handler);

		addGetRoute("/api/test/newpeer", exchange -> {
			String key = getParameter(exchange, "key").orElse(null);
			String anchor = getParameter(exchange, "anchor").orElse("0");
			String high = getParameter(exchange, "high").orElse(String.valueOf(ShardSpace.SHARD_CHUNK_RANGE - 1));
			String low = getParameter(exchange, "low").orElse(String.valueOf(-ShardSpace.SHARD_CHUNK_RANGE));
			String ip = getParameter(exchange, "ip").orElse(null);
			String port = getParameter(exchange, "port").orElse("-1"); // Defaults to universe port
			respond(TestService.getInstance().newPeer(key, anchor, high, low, ip, port), exchange);
		}, handler);
	}

    private void addTestRoutesTo(RoutingHandler handler) {
    	addGetRoute("/api/internal/ledger/metadata", exchange -> {
    		respond(InternalService.getInstance().ledgerMetaData(), exchange);
    	}, handler);
    	addGetRoute("/api/internal/atoms/dump", exchange -> {
    		boolean verbose = getParameter(exchange, "verbose").map(Boolean::valueOf).orElse(false);
    		respond(InternalService.getInstance().dumpAtoms(verbose), exchange);
    	}, handler);
    	addGetRoute("/api/internal/shards/dump", exchange -> {
    		respond(InternalService.getInstance().dumpShardChunks(), exchange);
    	}, handler);
    }

    private void addPostRoutesTo(RoutingHandler handler) {
        HttpHandler rpcPostHandler = new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) {
                // we need to be in another thread to do blocking io work, which is needed to extract the entire message body
                if (exchange.isInIoThread()) {
                    exchange.dispatch(this);
                    return;
                }

                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, CONTENT_TYPE_JSON);
	            try {
		            exchange.getResponseSender().send(jsonRpcServer.handleChecked(exchange));
	            } catch (IOException e) {
		            exchange.setStatusCode(400);
		            exchange.getResponseSender().send("Invalid request: " + e.getMessage());
	            }
            }
        };
        handler.add(Methods.POST, "/rpc", rpcPostHandler);
        handler.add(Methods.POST, "/rpc/", rpcPostHandler); // handle both /rpc and /rpc/ for usability
    }

    private void addRestRoutesTo(RoutingHandler handler) {
        // TODO: organize routes in a nicer way
        // System routes
        addRestSystemRoutesTo(handler);

        // Network routes
        addRestNetworkRoutesTo(handler);

        // Graph routes
        addRestGraphRoutesTo(handler);

        // Atom Model JSON schema
        addGetRoute("/schemas/atom.schema.json", exchange -> {
			respond(AtomSchemas.getJsonSchemaString(4), exchange);
        }, handler);

        // Misc routes
        addGetRoute("/api/atoms", exchange -> {
            String clazz = getParameter(exchange, "clazz").orElse(null);
            String aid = getParameter(exchange, "aid").orElse(null);
            String uid = getParameter(exchange, "uid").orElse(null);
            String address = getParameter(exchange, "address").orElse(null);
            String action = getParameter(exchange, "action").orElse(null);
            String destination = getParameter(exchange, "destination").orElse(null);
            String index = getParameter(exchange, "index").orElse(null);
            String time = getParameter(exchange, "time").orElse(null);
            String limit = getParameter(exchange, "limit").orElse(null);

			respond(
				atomsService.getAtoms(
					clazz,
					aid,
					uid,
					address,
					action,
					destination,
					index,
					time,
					limit
				), exchange);
        }, handler);

        addGetRoute("/api/atoms/byshard", exchange -> {
			String from = getParameter(exchange, "from").orElse(null);
			String to = getParameter(exchange, "to").orElse(null);
			respond(atomsService.getAtomsByShardRange(from, to), exchange);
		}, handler);

        addGetRoute("/api/atoms/conflict/:uid", exchange -> {
			String uid = getParameter(exchange, "from").orElse(null);
			respond(atomsService.getConflict(uid), exchange);
		}, handler);

        addGetRoute("/api/events", exchange -> {
			JSONObject eventCount = new JSONObject();
			atomsService.getAtomEventCount().forEach((k, v) -> eventCount.put(k.name(), v));
			respond(eventCount, exchange);
        }, handler);

        addGetRoute("/api/universe", exchange
                -> respond(UniverseService.getInstance().getUniverse(), exchange), handler);

        addGetRoute("/api/system/modules/api/tasks-waiting", exchange
                -> {
            JSONObject waiting = new JSONObject();
            waiting.put("count", atomsService.getWaitingCount());

            respond(waiting, exchange);
        }, handler);

		addGetRoute("/api/system/modules/api/websockets", exchange -> {
			JSONObject count = new JSONObject();
			count.put("count", getPeers().size());
			respond(count, exchange);
		}, handler);

        // delete method to disconnect all peers
        addRoute("/api/system/modules/api/websockets", Methods.DELETE_STRING, exchange -> {
            JSONObject result = this.disconnectAllPeers();
            respond(result, exchange);
        }, handler);

		// RTP
        addGetRoute("/api/rtp", exchange -> {
			JSONObject rtp = new JSONObject();
			// fixme might not be a deviation available if NTP switched off
			rtp.put("deviation", Modules.get(RTPService.class).getDeviation());
			rtp.put("offset", Modules.get(RTPService.class).getOffset());
			rtp.put("radix_time", Modules.get(RTPService.class).getUTCTimeMS());
			rtp.put("last_group_size", Modules.get(RTPService.class).getLastGroupSize());
			if(Modules.get(RuntimeProperties.class).get("rtp.naughty", false))
				rtp.put("skew", Modules.get(RuntimeProperties.class).get("rtp.skew", 10));
			else
				rtp.put("skew", 0);
			rtp.put("max_deviation", Modules.get(RTPService.class).getMaxDeviation());
			rtp.put("min_deviation", Modules.get(RTPService.class).getMinDeviation());
			rtp.put("max_offset", Modules.get(RTPService.class).getMaxOffset());
			rtp.put("min_offset", Modules.get(RTPService.class).getMinOffset());
			rtp.put("max_group_size", Modules.get(RTPService.class).getMaxGroupSize());
			rtp.put("bad_rounds", Modules.get(RTPService.class).getBadRounds());
			rtp.put("is_synchronized", Modules.get(RTPService.class).isSynchronized());
			rtp.put("number_of_peers", Modules.get(RTPService.class).getNumberOfPeers());

			respond(rtp, exchange);
		}, handler);

        addGetRoute("/api/latest-events", exchange -> {
        	JSONArray events = new JSONArray();
        	atomsService.getEvents().forEach(events::put);
        	respond(events, exchange);
		}, handler);

        addGetRoute("/api/rtp/params", exchange -> {
        	JSONObject rtp = new JSONObject();
			rtp.put("rtp.interval", Modules.get(RTPService.class).getInterval());
			rtp.put("rtp.age_threshold", Modules.get(RTPService.class).getAgeThreshold());
			rtp.put("rtp.decay", Modules.get(RTPService.class).getDecay());
			rtp.put("rtp.min_group_size", Modules.get(RTPService.class).getMinGroupSize());
			rtp.put("rtp.max_group_size", Modules.get(RTPService.class).getGroupSize());
			rtp.put("rtp.max_correction", Modules.get(RTPService.class).getMaxCorrection());
			rtp.put("rtp.alpha_trim_factor", Modules.get(RTPService.class).getAlphaTrimFactor());
			rtp.put("rtp.target_coupling", Modules.get(RTPService.class).getTargetCoupling());
			rtp.put("rtp.min_offset", Modules.get(RTPService.class).getRtpMinOffset());
			rtp.put("rtp.max_deviation", Modules.get(RTPService.class).getRtpMaxDeviation());
			rtp.put("rtp.max_start_deviation", Modules.get(RTPService.class).getMaxStartDeviation());
			rtp.put("rtp.test", Modules.get(RTPService.class).getTest());
			rtp.put("rtp.naughty", Modules.get(RTPService.class).getNaughty());
			rtp.put("rtp.skew", Modules.get(RTPService.class).getSkew());
			rtp.put("rtp.clock_offset", Modules.get(RTPService.class).getClockOffset());

			respond(rtp, exchange);
		}, handler);

        addGetRoute("/api/rtp/timestamp", exchange -> {
			RTPTimestamp ts = Modules.get(RTPService.class).getRTPTimestamp();
			JSONObject rtp = new JSONObject();
			rtp.put("nid", ts.getNid().toString());
			rtp.put("radix_time", ts.getRadixTime());
			rtp.put("node_time", ts.getNodeTime());
			rtp.put("isSynced", ts.isSynced());
			rtp.put("ntp_deviation", ts.getNtpDeviation());

			respond(rtp, exchange);
		}, handler);

		// keep-alive
        addGetRoute("/api/ping", exchange
            -> respond(
                new JSONObject().put("response", "pong").put("timestamp", System.currentTimeMillis()),
                exchange),
            handler);
    }

    private void addRestGraphRoutesTo(RoutingHandler handler) {
        addGetRoute("/api/graph/node/mass", exchange
                -> {
            String timestamp = getParameter(exchange, "timestamp").orElse(Long.toString(Long.MAX_VALUE));
            respond(GraphService.getInstance().getNodeMasses(timestamp).toString(), exchange);
        }, handler);

        addGetRoute("/api/graph/node/mass/{nid}", exchange
                -> {
            String nid = getParameter(exchange, "nid").orElse(null);
            String timestamp = getParameter(exchange, "timestamp").orElse(null);

            respond(GraphService.getInstance().getNodeMass(nid, timestamp), exchange);
        }, handler);

        addGetRoute("/api/graph/route", exchange -> {
        	String timestamp = getParameter(exchange, "timestamp").orElseGet(() -> String.valueOf(Time.currentTimestamp()));
			respond(GraphService.getInstance().getRoutingTable(LocalSystem.getInstance().getNID().toString(), timestamp), exchange);
		}, handler);

        addGetRoute("/api/graph/route/{nid}", exchange
                -> {
            String nid = getParameter(exchange, "nid").orElse(null);
            String timestamp = getParameter(exchange, "timestamp").orElse(null);

            respond(GraphService.getInstance().getNodeMass(nid, timestamp), exchange); // TODO WTF?
        }, handler);
    }

    private void addRestNetworkRoutesTo(RoutingHandler handler) {
        addGetRoute("/api/network", exchange -> {
            respond(NetworkService.getInstance().getNetwork(), exchange);
        }, handler);
        addGetRoute("/api/network/peers/live", exchange
                -> respond(NetworkService.getInstance().getLivePeers().toString(), exchange), handler);
        addGetRoute("/api/network/nids/live", exchange -> {
        	String planck = getParameter(exchange, "planck").orElse(null);
			if (planck == null)
				respond(NetworkService.getInstance().getLiveNIDS(), exchange);
			else
				respond(NetworkService.getInstance().getLiveNIDS(planck), exchange);
		}, handler);
        addGetRoute("/api/network/peers", exchange
                -> respond(NetworkService.getInstance().getPeers().toString(), exchange), handler);
        addGetRoute("/api/network/peers/{id}", exchange
                -> respond(NetworkService.getInstance().getPeer(getParameter(exchange, "id").orElse(null)), exchange), handler);

    }

    private void addRestSystemRoutesTo(RoutingHandler handler) {
        addGetRoute("/api/system", exchange
                -> respond(AdminService.getInstance().getSystem(), exchange), handler);
        addGetRoute("/api/system/profiler", exchange
                -> respond(AdminService.getInstance().getProfiler(), exchange), handler);
        addGetRoute("/api/system/modules", exchange
                -> respond(AdminService.getInstance().getModules(), exchange), handler);
        addGetRoute("/api/system/modules/atom-syncer", exchange
                -> respond(AdminService.getInstance().getModules(), exchange), handler);
    }

    // helper methods for responding to an exchange with various objects for readability
    private void respond(String object, HttpServerExchange exchange) {
        exchange.getResponseSender().send(object);
    }

    private void respond(JSONObject object, HttpServerExchange exchange) {
        exchange.getResponseSender().send(object.toString());
    }

    private void respond(JSONArray object, HttpServerExchange exchange) {
        exchange.getResponseSender().send(object.toString());
    }

    /**
     * Close and remove a certain peer
     *
     * @param peer The peer to remove
     */
    /*package*/ void closeAndRemovePeer(RadixJsonRpcPeer peer) {
        peers.remove(peer);
        peer.close();
    }

    /**
     * Disconnect all currently connected peers
     *
     * @return Json object containing disconnect information
     */
	public final JSONObject disconnectAllPeers() {
		JSONObject result = new JSONObject();
		JSONArray closed = new JSONArray();
		result.put("closed", closed);
		HashMap<RadixJsonRpcPeer, WebSocketChannel> peersCopy = new HashMap<>(peers);

		peersCopy.forEach((peer, ws) -> {
			JSONObject closedPeer = new JSONObject();
			closedPeer.put("isOpen", ws.isOpen());
			closedPeer.put("closedReason", ws.getCloseReason());
			closedPeer.put("closedCode", ws.getCloseCode());
			closed.put(closedPeer);

			closeAndRemovePeer(peer);
			try {
				ws.close();
			} catch (IOException e) {
				logger.error("Error while closing web socket: " + e, e);
			}
		});

		result.put("closedCount", peersCopy.size());

		return result;
	}

    /**
     * Add a GET method route with JSON content a certain path and consumer to the given handler
     *
     * @param prefixPath       The prefix path
     * @param responseFunction The consumer that processes incoming exchanges
     * @param routingHandler   The routing handler to add the route to
     */
    private static void addGetRoute(String prefixPath, ManagedHttpExchangeConsumer responseFunction, RoutingHandler routingHandler) {
        addRoute(prefixPath, Methods.GET_STRING, responseFunction, routingHandler);
    }

    /**
     * Add a route with JSON content and a certain path, method, and consumer to the given handler
     *
     * @param prefixPath       The prefix path
     * @param method           The HTTP method
     * @param responseFunction The consumer that processes incoming exchanges
     * @param routingHandler   The routing handler to add the route to
     */
    private static void addRoute(String prefixPath, String method, ManagedHttpExchangeConsumer responseFunction, RoutingHandler routingHandler) {
        addRoute(prefixPath, method, CONTENT_TYPE_JSON, responseFunction::accept, routingHandler);
    }

    /**
     * Add a route with a certain path, method, content type and consumer to the given handler
     *
     * @param prefixPath       The prefix path
     * @param method           The HTTP method
     * @param contentType      The MIME type
     * @param responseFunction The consumer that processes incoming exchanges
     * @param routingHandler   The routing handler to add the route to
     */
    private static void addRoute(String prefixPath, String method, String contentType, ManagedHttpExchangeConsumer responseFunction, RoutingHandler routingHandler) {
        routingHandler.add(method, prefixPath, exchange -> {
            exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, contentType);
            responseFunction.accept(exchange);
        });
    }

    /**
     * Get a parameter from either path or query parameters from an http exchange.
     * Note that path parameters are prioritised over query parameters in the event of a conflict.
     *
     * @param exchange The exchange to get the parameter from
     * @param name     The name of the parameter
     * @return The parameter with the given name from the path or query parameters, or empty if it doesn't exist
     */
    private static Optional<String> getParameter(HttpServerExchange exchange, String name) {
        // our routing handler puts path params into query params by default so we don't need to include them manually
        return Optional.ofNullable(exchange.getQueryParameters().get(name)).map(Deque::getFirst);
    }
}

