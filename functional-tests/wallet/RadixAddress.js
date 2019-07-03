const bs58 = require('bs58');
const RadixUniverse = require('./RadixUniverse');
const RadixUtil = require('./RadixUtil');
const RadixPublicKey = require('./RadixPublicKey');

module.exports = class RadixAddress {

  constructor(data) {
    this.universe = new RadixUniverse(require('./universe_development.json'));
    this.address = '';
    this.publicKey = null;
    this.raw = 0;

    if (data instanceof Array) {
      this.fromArray(data);
    } else {
      this.fromString(data);
    }
  }

  fromString(address) {
    let raw = Array.prototype.slice.call(bs58.decode(address), 0);

    // if (!RadixUniverse.getUniverseByMagic(raw[0])) {
    //     throw 'Unknown magic byte' + raw[0];
    // }

    const check = RadixUtil.hash(raw.splice(0, raw.length - 4), 0, raw.length - 4);
    for (let i = 0; i < 4; i++) {
      if (check[i] !== raw[raw.length - 4 + i]) {
        throw Error(`Address ${address} checksum mismatch`);
      }
    }

    raw = Array.prototype.slice.call(bs58.decode(address), 0);

    this.address = address;
    this.publicKey = new RadixPublicKey(raw.splice(1, raw.length - 5));
  }

  fromArray(publicKey) {
    if (!publicKey) {
      throw Error('Missing public key');
    }
    if (publicKey.length != 33) {
      throw Error(`Public key must be 33 bytes, but was ${publicKey.length}`);
    }

    const addressBytes = [];

    addressBytes[0] = this.universe.getMagic() & 0xff;
    for (let i = 0; i < publicKey.length; i++) {
      addressBytes[i + 1] = publicKey[i];
    }

    const check = RadixUtil.hash(addressBytes, 0, publicKey.length + 1);
    for (let i = 0; i < 4; i++) {
      addressBytes[publicKey.length + 1 + i] = check[i];
    }

    this.address = bs58.encode(addressBytes);
    this.publicKey = new RadixPublicKey(publicKey);
  }

  toString() {
    return this.address;
  }

  getEUID() {
    return this.publicKey.getUID();
  }

}
