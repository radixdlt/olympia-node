module.exports = class RadixKeyPair {

  constructor(publicKey, privateKey) {
    this.publicKey = publicKey;
    this.privateKey = privateKey;
  }

  getPublicKey() {
    return this.publicKey;
  }

}
