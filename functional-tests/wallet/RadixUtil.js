const EC = require('elliptic').ec;
const shajs = require('sha.js');
const BN = require('bn.js');
const RadixKeyPair = require('./RadixKeyPair');

function generateKeyPair() {
  // Create and initialize EC context
  // (better do it once and reuse it)
  const ec = new EC('secp256k1');

  // Generate keys
  const key = ec.genKeyPair();

  const buffer = Buffer.from(key.getPublic().encodeCompressed('hex'), 'hex');
  const publicKey = Array.prototype.slice.call(buffer, 0);

  return new RadixKeyPair(publicKey, key.getPrivate('hex'));
}

function hash(data, offset, len) {
  return Array.prototype.slice.call(Buffer.from(shajs('sha256').update(data, offset, len).digest('base64'), 'base64'), 0);
}

function bigIntFromByteArray(bytes) {
  return new BN(bytes).fromTwos(bytes.length * 8);
}


function byteArrayFromBigInt(number) {
  return number.toTwos(8 * number.byteLength()).toBuffer();
}

function getInt8(value) {
  return value << 24 >> 24;
}

module.exports = { generateKeyPair, hash, bigIntFromByteArray, byteArrayFromBigInt, getInt8 };
