const RadixPublicKey = require('./RadixPublicKey');

module.exports = class RadixUniverse {

  constructor(obj) {
    this.port = obj.port;
    this.name = obj.name;
    this.description = obj.description;
    this.type = obj.type;
    this.timestamp = obj.timestamp;
    this.creator = new RadixPublicKey(Array.prototype.slice.call(Buffer.from(obj.creator.value, 'base64'), 0));
    this.magic = obj.magic;
    this.universes = ['RADIX_DEVELOPMENT'];
  }

  getMagic() {
    return this.magic;
  }

  getMagicByte() {
    return this.magic & 0xff;
  }

  fromMagic(magic) {
    for (let i = 0; i < this.universes.length; i++) {
      if (magic == (this.universes[i].hashCode() & 0xff)) {
        return this.universes[i];
      }
    }
  }

  toString() {
    return this.name;
  }

}

String.prototype.hashCode = function () {
  let hash = 0;
  if (this.length == 0) return hash;
  for (let i = 0; i < this.length; i++) {
    let char = this.charCodeAt(i);
    hash = ((hash << 5) - hash) + char;
    hash = hash & hash; // Convert to 32bit integer
  }
  return hash;
};
