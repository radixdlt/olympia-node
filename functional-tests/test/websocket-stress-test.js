const WebSocket = require('ws');
const assert = require('assert');
const RadixAddress = require('../wallet/RadixAddress');
const RadixUtil = require('../wallet/RadixUtil');

describe('Websocket Stress', () => {
  const address = new RadixAddress(RadixUtil.generateKeyPair().getPublicKey());
  const numAtoms = 10000;

  it(`Should send ${numAtoms} atoms to self while subscribed and receive it all`, (done) => {
    const messages = [];
    for (let i = 0; i < numAtoms; i++) {
      const randomMessage = `Hello World! ${Math.random()}`;
      const randomMessageBase64 = Buffer.from(randomMessage).toString('base64');
      messages.push(randomMessageBase64);
    }
    const ws = new WebSocket('ws://localhost:8080/rpc', {});
    const subscriberId = `stress-test-${Math.random()}`;
    ws.on('message', (data) => {
      const msg = JSON.parse(data);
      if (typeof (msg.id) === 'number') {
        assert.deepEqual(msg.result, { 'status': 'submitted' }, JSON.stringify(msg));
        if (msg.id === messages.length - 1) {
          ws.send(JSON.stringify({
            'method': 'Atoms.subscribe',
            'id': 'hello',
            'params': {
              'subscriberId': subscriberId,
              'query': {
                'destinationAddress': address.toString()
              },
            },
          }));
        }
      } else if (msg.id === 'hello') {
        assert.deepEqual(msg.result, { 'success': true }, `Subscribe failed: ${data}`)
      } else if (msg.method === 'Radix.welcome') {
        // do nothing
      } else if (msg.method === 'Atoms.subscribeUpdate' && msg.params.subscriberId === subscriberId) {
        msg.params.atoms.filter(atom => atom.serializer === 2358711).forEach((atom) => {
          const index = messages.findIndex(message => message === atom.encrypted.value);
          if (index < 0) {
            // do nothing
          } else {
            messages.splice(index, 1);
            if (messages.length === 0) {
              done();
              ws.close();
            }
          }
        });
      } else {
        done(`Unknown msg received: ${data}`);
      }
    });
    ws.on('open', () => {
      for (let i = 0; i < messages.length; i++) {
        ws.send(JSON.stringify({
          'method': 'Universe.submitAtom',
          'params':
            {
              'encrypted': {
                'serializer': 'BASE64',
                'value': messages[i],
              },
              'destinations': [
                {
                  'serializer': 'EUID',
                  'value': address.getEUID()
                },
              ],
              'serializer': 2358711,
              'action': 'STORE',
              'version': 100,
            },
          'id': i,
        }));
      }
    });
  }).timeout(120000);

  it(`Should send ${numAtoms} atoms to self and then subscribe and receive it all`, (done) => {
    const messages = [];
    for (let i = 0; i < numAtoms; i++) {
      const randomMessage = `Hello World! ${Math.random()}`;
      const randomMessageBase64 = Buffer.from(randomMessage).toString('base64');
      messages.push(randomMessageBase64);
    }
    const ws = new WebSocket('ws://localhost:8080/rpc', {});
    const subscriberId = `stress-test-${Math.random()}`;
    ws.on('message', (data) => {
      const msg = JSON.parse(data);
      if (typeof (msg.id) === 'number') {
        assert.deepEqual(msg.result, { 'status': 'submitted' }, JSON.stringify(msg));
      } else if (msg.id === 'hello') {
        assert.deepEqual(msg.result, { 'success': true }, `Subscribe failed: ${data}`)
      } else if (msg.method === 'Radix.welcome') {
        // do nothing
      } else if (msg.method === 'Atoms.subscribeUpdate' && msg.params.subscriberId === subscriberId) {
        msg.params.atoms.filter(atom => atom.serializer === 2358711).forEach((atom) => {
          const index = messages.findIndex(message => message === atom.encrypted.value);
          if (index < 0) {
            // do nothing
          } else {
            messages.splice(index, 1);
            if (messages.length === 0) {
              done();
              ws.close();
            }
          }
        });
      } else {
        done(`Unknown msg received: ${data}`);
      }
    });
    ws.on('open', () => {
      for (let i = 0; i < messages.length; i++) {
        ws.send(JSON.stringify({
          'method': 'Universe.submitAtom',
          'params':
            {
              'encrypted': {
                'serializer': 'BASE64',
                'value': messages[i],
              },
              'destinations': [
                {
                  'serializer': 'EUID',
                  'value': address.getEUID()
                },
              ],
              'serializer': 2358711,
              'action': 'STORE',
              'version': 100,
            },
          'id': i,
        }));
      }
      ws.send(JSON.stringify({
        'method': 'Atoms.subscribe',
        'id': 'hello',
        'params': {
          'subscriberId': subscriberId,
          'query': {
            'destinationAddress': address.toString()
          },
        },
      }));
    });
  }).timeout(120000);
});
