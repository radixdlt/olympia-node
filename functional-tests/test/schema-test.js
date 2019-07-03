const Ajv = require('ajv');
const assert = require('assert');
const axios = require('axios');
const WebSocket = require('ws');

const objectSchemas = [
  'atom',
  'mailatom',
  'nullatom',
  'particle',
];

const schemaTests = [{
  'name': 'atomsQuery',
  'exampleExpectedResponse': 'atomsQuery_Response',
}, {
  'name': 'getAtomInfo',
  'exampleExpectedResponse': 'Error',
}, {
  'name': 'getPeers',
  'exampleExpectedResponse': 'getPeers_Response',
}, {
  'name': 'getRecentAtoms',
  'exampleExpectedResponse': 'getRecentAtoms_Response',
}, {
  'name': 'submitAtom',
  'exampleExpectedResponse': 'Response',
}];

axios.defaults.baseURL = 'http://localhost:8080';

const ajv = new Ajv();
ajv.addMetaSchema(require('ajv/lib/refs/json-schema-draft-06.json'));

(async () => {
  objectSchemas.forEach(async (schema) => {
    const response = await axios.get(`/schemas/${schema}`);
    ajv.addSchema(response.data);
  });
})();

const validatePOST = async (schemaTestConfig) => {
  const reqPath = `/schemas/JSONRPC_${schemaTestConfig.name}_Request`;
  const resPath = `/schemas/JSONRPC_${schemaTestConfig.exampleExpectedResponse}`;

  const reqResponse = await axios.get(reqPath);
  const reqSchema = reqResponse.data;
  const { examples } = reqSchema;
  const rpcResponse = await axios.post('/rpc', examples[0]);

  const resResponse = await axios.get(resPath);
  const resSchema = resResponse.data;

  const validate = ajv.compile(resSchema);
  const valid = validate(rpcResponse.data);
  assert.equal(valid, true, `Response Invalid \n${JSON.stringify(rpcResponse.data)} \n${JSON.stringify(validate.errors)}`);
};

describe('JSON RPC POST API Response Validation', () => {

  schemaTests.forEach((schemaTestConfig) => {
    it(schemaTestConfig.name, async () => {
      await validatePOST(schemaTestConfig);
    });
  });

});

describe('Websocket Subscribe Response Validation', () => {

  it('Should update notification validation', (done) => {
    (async () => {
      const subscribeRequestResponse = await axios.get('/schemas/JSONRPC_subscribe_Request');
      const subscribeRequest = subscribeRequestResponse.data.examples[0];

      const subscribeResponseSchemaResponse = await axios.get('/schemas/JSONRPC_subscribe_Response');
      const subscribeResponseSchema = subscribeResponseSchemaResponse.data;
      const validateResponse = ajv.compile(subscribeResponseSchema);

      const notificationSchemaResponse = await axios.get('/schemas/JSONRPC_subscribe_UpdateNotification');
      const notificationSchema = notificationSchemaResponse.data;
      const validateNotification = ajv.compile(notificationSchema);

      const submitAtomRequestResponse = await axios.get('/schemas/JSONRPC_submitAtom_Request');
      const submitAtomRequest = submitAtomRequestResponse.data.examples[0];

      assert(subscribeRequest.id, `Subscribe Request example has no id: \n${subscribeRequest}`);

      const ws = new WebSocket('ws://localhost:8080/rpc');
      ws.on('message', (data) => {
        const msg = JSON.parse(data);
        if (msg.id === subscribeRequest.id) {
          const valid = validateResponse(msg);
          assert(valid, `Subscribe Response Invalid \n${data} \n${JSON.stringify(validateResponse.errors)}`);
        } else if (msg.id === submitAtomRequest.id) {
          // do nothing
        } else if (msg.method) {
          if (msg.method === 'Radix.welcome') {
            // TODO: validate this
          } else {
            const valid = validateNotification(msg);
            assert(valid, `Notification Invalid \n${data} \n${JSON.stringify(validateNotification.errors)}`);
            ws.close();
            done();
          }
        }
      });
      ws.on('open', () => {
        ws.send(JSON.stringify(subscribeRequest));
        ws.send(JSON.stringify(submitAtomRequest));
      });
    })();
  });

});

