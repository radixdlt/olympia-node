const Ajv = require('ajv');
const assert = require('assert');
const axios = require('axios');

const objectSchemas = [
  'base64',
  'atom',
  'basicpayloadatom',
  'nullatom',
  'particle',
];

const jsonRpcSchemas = [
  'JSONRPC_atomsQuery_Request',
  'JSONRPC_atomsQuery_Response',
  'JSONRPC_Error',
  'JSONRPC_getAtomInfo_Request',
  'JSONRPC_getAtomInfo_Response',
  'JSONRPC_getLivePeers_Request',
  'JSONRPC_getPeers_Request',
  'JSONRPC_getPeers_Response',
  'JSONRPC_getRecentAtoms_Request',
  'JSONRPC_getRecentAtoms_Response',
  'JSONRPC_Response',
  'JSONRPC_submitAtom_Request',
  'JSONRPC_subscribe_Request',
  'JSONRPC_subscribe_Response',
  'JSONRPC_subscribe_UpdateNotification',
];

axios.defaults.baseURL = 'http://localhost:8080';

const ajv = new Ajv();
ajv.addMetaSchema(require('ajv/lib/refs/json-schema-draft-06.json'));

(async () => {
  objectSchemas.forEach(async (schema) => {
    const response = await axios.get(`/schemas/${schema}`);
    ajv.addSchema(response.data);
  })
})();

describe('JSON Schema Examples Validation', () => {

  objectSchemas.concat(jsonRpcSchemas).forEach((schemaName) => {
    it(`"${schemaName}" should contain an example and should pass validation`, async () => {
      const response = await axios.get(`/schemas/${schemaName}`);
      const schema = response.data;
      const validate = ajv.compile(schema);

      assert(schema.examples.length > 0);

      const valid = validate(schema.examples[0]);
      assert.equal(valid, true, `Example invalid \n${JSON.stringify(schema.examples[0])} \n${JSON.stringify(validate.errors)}`);
    });
  });

});
