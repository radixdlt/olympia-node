const RadixUtil = require('./RadixUtil');

module.exports = class RadixPublicKey {

  constructor(publicKey) {
    for (let i = 0; i < publicKey.length; i++) {
      publicKey[i] = RadixUtil.getInt8(publicKey[i]);
    }
    this.publicKey = publicKey;
  }

  length() {
    return this.publicKey.length;
  }

  hash() {
    const hash = RadixUtil.hash(this.publicKey, 0, this.publicKey.length);
    for (let i = 0; i < hash.length; i++) {
      hash[i] = RadixUtil.getInt8(hash[i]);
    }
    return hash;
  }

  getUID() {
    const hash = this.hash();
    return RadixUtil.bigIntFromByteArray(Buffer.from(hash.slice(0, 12))).toString();
  }

}
