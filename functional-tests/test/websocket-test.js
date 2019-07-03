const WebSocket = require('ws');
const assert = require('assert');
const RadixAddress = require('../wallet/RadixAddress');
const RadixUtil = require('../wallet/RadixUtil');

describe('Websockets', () => {

  const address = new RadixAddress(RadixUtil.generateKeyPair().getPublicKey());

  it('Should not close socket on invalid address', (done) => {
    const ws = new WebSocket('ws://localhost:8080/rpc', {});
    ws.on('message', (data) => {
      // console.log(data);
    });
    let finished = false;
    ws.on('open', () => {
      ws.send(JSON.stringify({
        'method': 'Atoms.subscribe',
        'id': 'hello',
        'params': {
          'destinationAddress': address.toString(),
        },
      }));
      setTimeout(() => {
        finished = true;
        done();
      }, 1000);
    });
    ws.on('close', () => {
      if (!finished) {
        done('Closed socket early');
      }
    });
  });

  it(`Should send a random payload atom to the address "${address.toString()}" and receive it`, (done) => {
    const ws = new WebSocket('ws://localhost:8080/rpc', {});
    const randomMessage = `Hello World! ${Math.random()}`;
    const randomMessageBase64 = Buffer.from(randomMessage).toString('base64');

    ws.on('message', (data) => {
      const msg = JSON.parse(data);
      if (msg.id === 2) {
        assert.deepEqual(msg.result, { 'status': 'submitted' }, JSON.stringify(msg));
      } else if (msg.id === 'hello') {
        assert.deepEqual(msg.result, { 'success': true }, `Subscribe failed: ${data}`);
      } else if (msg.method === 'Radix.welcome') {
        // nothing
      } else if (msg.method === 'Atoms.subscribeUpdate' && msg.params.subscriberId === 123456) {
        if (msg.params.atoms.find(atom => atom.encrypted && atom.encrypted.value === randomMessageBase64)) {
          done();
        }
      } else {
        done(`Unknown msg received: ${data}`);
      }
    });

    ws.on('open', () => {
      ws.send(JSON.stringify({
        'method': 'Atoms.subscribe',
        'id': 'hello',
        'params': {
          'subscriberId': 123456,
          'query': {
            'destinationAddress': address.toString(),
          },
        },
      }));
      ws.send(JSON.stringify(
        {
          'method': 'Universe.submitAtom',
          'params':
            {
              'encrypted': {
                'serializer': 'BASE64',
                'value': randomMessageBase64,
              },
              'destinations': [
                {
                  'serializer': 'EUID',
                  'value': address.getEUID(),
                }
              ],
              'serializer': -257259791,
              'action': 'STORE',
              'version': 100,
            },
          'id': 2,
        }));
    });
  });

  it('Should multiplex websocket over two subscriber IDs', (done) => {
    const ws = new WebSocket('ws://localhost:8080/rpc', {});
    const randomMessage = `Hello World! ${Math.random()}`;
    const randomMessageBase64 = Buffer.from(randomMessage).toString('base64');
    const subscribers = [
      { 'id': 'first', 'found': false },
      { 'id': 'second', 'found': false },
    ];

    ws.on('message', (data) => {
      const msg = JSON.parse(data);
      if (msg.id === 2) {
        assert.deepEqual(msg.result, { 'status': 'submitted' }, JSON.stringify(msg))
      } else if (msg.id === 'hello-first') {
        assert.deepEqual(msg.result, { 'success': true }, `First subscribe failed: ${data}`);
      } else if (msg.id === 'hello-second') {
        assert.deepEqual(msg.result, { 'success': true }, `Second subscribe failed: ${data}`);
      } else if (msg.method === 'Radix.welcome') {
        // do nothing
      } else if (msg.method === 'Atoms.subscribeUpdate') {
        if (msg.params.atoms.find(atom => atom.encrypted && atom.encrypted.value === randomMessageBase64)) {
          subscribers.find(subscribers => subscribers.id === msg.params.subscriberId).found = true;
          if (subscribers.reduce((found, cur) => found && cur.found, true)) {
            done();
          }
        }
      } else {
        done(`Unknown msg received: ${data}`);
      }
    });
    ws.on('open', () => {
      subscribers.forEach((subscriber) => {
        ws.send(JSON.stringify({
          'method': 'Atoms.subscribe',
          'id': 'hello' + '-' + subscriber.id,
          'params': {
            'subscriberId': subscriber.id,
            'query': {
              'destinationAddress': address.toString(),
            },
          },
        }));
      });
      ws.send(JSON.stringify({
        'method': 'Universe.submitAtom',
        'params':
          {
            'encrypted': {
              'serializer': 'BASE64',
              'value': randomMessageBase64,
            },
            'destinations': [
              {
                'serializer': 'EUID',
                'value': address.getEUID(),
              }
            ],
            'serializer': -257259791,
            'action': 'STORE',
            'version': 100,
          },
        'id': 2,
      }));
    });
  });

  it('Should send plain random text and throw an error', (done) => {
    const ws = new WebSocket('ws://localhost:8080/rpc', {});
    ws.on('message', (data) => {
      if (data.indexOf('error') !== -1) {
        done();
      }
    });
    ws.on('open', async () => {
      ws.send(Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15));
    });
  });

});
