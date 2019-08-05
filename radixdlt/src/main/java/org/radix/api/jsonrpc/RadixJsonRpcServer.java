package org.radix.api.jsonrpc;

import com.google.common.io.CharStreams;
import com.radixdlt.atomos.RadixAddress;
import org.radix.atoms.Atom;
import com.radixdlt.atoms.AtomStatus;
import com.radixdlt.common.AID;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import io.undertow.server.HttpServerExchange;
import java.util.Objects;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.radix.api.AtomQuery;
import org.radix.api.services.AtomsService;
import org.radix.atoms.AtomDiscoveryRequest;
import org.radix.atoms.AtomStore;
import org.radix.atoms.sync.AtomSync;
import org.radix.modules.Modules;
import org.radix.network.peers.PeerHandler;
import org.radix.network.peers.PeerHandler.PeerDomain;
import org.radix.network.peers.filters.PeerFilter;
import com.radixdlt.universe.Universe;
import org.radix.universe.system.LocalSystem;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Stateless Json Rpc 2.0 Server
 */
public final class RadixJsonRpcServer {
	private static final long DEFAULT_MAX_REQUEST_SIZE = 1024L * 1024L;

	/**
	 * Maximum request size in bytes
	 */
	private final long maxRequestSizeBytes;

	/**
	 * Atom schema to first validate against
	 */
	private final Schema atomSchema;

	/**
	 * Service to submit atoms through
	 */
	private final AtomsService atomsService;

	/**
	 * Store to query atoms from
	 */
    private final AtomStore atomStore;

	private final AtomSync atomSync;

	/**
	 * Serialization mechanism
	 */
	private final Serialization serialization;

	public RadixJsonRpcServer(Serialization serialization, AtomStore atomStore, AtomSync atomSync, AtomsService atomsService, Schema atomSchema) {
		this(serialization, atomStore, atomSync, atomsService, atomSchema, DEFAULT_MAX_REQUEST_SIZE);
	}

	public RadixJsonRpcServer(Serialization serialization, AtomStore atomStore, AtomSync atomSync, AtomsService atomsService, Schema atomSchema, long maxRequestSizeBytes) {
		this.serialization = Objects.requireNonNull(serialization);
		this.atomStore = Objects.requireNonNull(atomStore);
		this.atomSync = Objects.requireNonNull(atomSync);
		this.atomsService = Objects.requireNonNull(atomsService);
		this.atomSchema = Objects.requireNonNull(atomSchema);
		this.maxRequestSizeBytes = Objects.requireNonNull(maxRequestSizeBytes);
	}

    /**
     * Extract a JSON RPC API request from an HttpServerExchange, handle it as usual and return the response
     *
     * @param exchange The JSON RPC API request
     * @return The response
     */
    public String handleChecked(HttpServerExchange exchange) throws IOException {
	    exchange.setMaxEntitySize(maxRequestSizeBytes);
	    exchange.startBlocking();
        String requestBody = CharStreams.toString(new InputStreamReader(exchange.getInputStream(), StandardCharsets.UTF_8));

        return this.handleChecked(requestBody);
    }

	/**
	 * Handle the string JSON-RPC request with size checks, return appropriate error if oversized
	 * @param jsonRpcRequest The string JSON-RPC request
	 * @return The response to the request, could be a JSON-RPC error
	 */
	String handleChecked(String jsonRpcRequest) {
		// one char is 2 bytes
	    if (jsonRpcRequest.length() * 2 > maxRequestSizeBytes) {
		    return JsonRpcUtil.errorResponse(
		        JSONObject.NULL,
			    JsonRpcUtil.OVERSIZED_REQUEST,
			    "request too big: " + jsonRpcRequest.length() * 2 + " > " + maxRequestSizeBytes
		    ).toString();
	    }

	    return handle(new JSONObject(jsonRpcRequest)).toString();
	}

    /**
     * Handle a certain JSON RPC request and return the response
     *
     * @param jsonRpcRequest The JSON RPC API request
     * @return The response
     */
    private JSONObject handle(JSONObject jsonRpcRequest) {
        Object id = null;
        try {
        	if (!jsonRpcRequest.has("id")) {
        		return JsonRpcUtil.errorResponse(JSONObject.NULL, JsonRpcUtil.INVALID_REQUEST_CODE, "id missing");
			}

            id = jsonRpcRequest.get("id");
			final Object result;
			final String method = jsonRpcRequest.getString("method");
			final Object paramsObject = jsonRpcRequest.get("params");
			switch (method) {
				case "Ledger.getAtom":
					if (!(paramsObject instanceof JSONObject)) {
						return JsonRpcUtil.errorResponse(id, -32000, "params should be a JSONObject" , new JSONObject());
					} else {
						JSONObject params = (JSONObject) paramsObject;
						if (!params.has("aid")) {
							return JsonRpcUtil.errorResponse(id, -32000, "aid not present", new JSONObject());
						}

						String aidString = params.getString("aid");
						Optional<Atom> foundAtom = atomStore.getAtom(AID.from(aidString));
						if (foundAtom.isPresent()) {
							result = foundAtom.get();
						} else {
							return JsonRpcUtil.errorResponse(id, -32000, "Atom not found", new JSONObject());
						}
					}

					break;
				case "Ledger.getAtoms":
					if (!(paramsObject instanceof JSONObject)) {
						return JsonRpcUtil.errorResponse(id, -32000, "No address present", new JSONObject());
					} else {
						JSONObject params = (JSONObject) paramsObject;
						if (!params.has("address")) {
							return JsonRpcUtil.errorResponse(id, -32000, "No address present", new JSONObject());
						}

						final String addressString = params.getString("address");
						final RadixAddress address = RadixAddress.from(addressString);

						final AtomDiscoveryRequest request = new AtomQuery(address).toAtomDiscovery();
						atomStore.discovery(request);
						result = request.getDelivered();
					}

					break;
				case "Universe.getUniverse":
					result = Modules.get(Universe.class);
					break;
                case "Network.getLivePeers":
                    result = Modules.get(PeerHandler.class).getPeers(PeerDomain.NETWORK, (PeerFilter) null, null);
                    break;
                case "Network.getPeers":
                    result = Modules.get(PeerHandler.class).getPeers(PeerDomain.PERSISTED, (PeerFilter) null, null);
                    break;
                case "Network.getInfo":
                    result = LocalSystem.getInstance();
                    break;
                case "Ping":
                    result = new JSONObject()
                        .put("response", "pong")
                        .put("timestamp", java.lang.System.currentTimeMillis());
                    break;
				case "Atoms.submitAtom":
					if (!(paramsObject instanceof JSONObject)) {
						return JsonRpcUtil.errorResponse(id, -32000, "No atom present", new JSONObject());
					} else {
						JSONObject jsonAtom = (JSONObject) paramsObject;

						try {
							atomSchema.validate(jsonAtom);
						} catch (ValidationException e) {
							return JsonRpcUtil.errorResponse(id, -32000, "Schema Error", e.toJSON());
						}

						final Atom atom = serialization.fromJsonObject(jsonAtom, Atom.class);
						final AtomStatus atomStatus = atomsService.submitAtom(atom);
						result = new JSONObject()
							.put("status", atomStatus.toString())
							.put("aid", atom.getAID())
							.put("timestamp", System.currentTimeMillis());
					}
					break;
				case "Atoms.getAtomStatus":
					if (!(paramsObject instanceof JSONObject) || !((JSONObject) paramsObject).has("aid")) {
						return JsonRpcUtil.errorResponse(id, -32000, "No aid present", new JSONObject());
					} else {
						String aidString = ((JSONObject) paramsObject).getString("aid");
						final AID aid = AID.from(aidString);
						AtomStatus atomStatus = atomSync.getAtomStatus(aid);
						if (atomStatus == null) {
							if (atomStore.hasAtom(aid)) {
								atomStatus = AtomStatus.STORED;
							} else {
								atomStatus = AtomStatus.DOES_NOT_EXIST;
							}
						}
						result = new JSONObject().put("status", atomStatus.toString());
					}
					break;
                default:
                    return JsonRpcUtil.methodNotFoundResponse(id);
            }

            JSONObject response = new JSONObject();
			response.put("id", id);
			response.put("jsonrpc", "2.0");

			// FIXME: Bit of a hack for now
			if (result instanceof List) {
				List<Object> list = (List<Object>) result;
				JSONArray resultArray = new JSONArray();
				list.stream().map(o -> serialization.toJsonObject(o, Output.API)).forEach(resultArray::put);
				response.put("result", resultArray);
			} else {
				response.put("result", serialization.toJsonObject(result, Output.API));
			}

			return response;

		} catch (Exception e) {
			if (jsonRpcRequest.has("params") && jsonRpcRequest.get("params") instanceof JSONObject) {
				return JsonRpcUtil.errorResponse(id,-32000, e.getMessage(), jsonRpcRequest.getJSONObject("params"));
			} else {
				return JsonRpcUtil.errorResponse(id,-32000, e.getMessage());
			}
		}
	}
}
