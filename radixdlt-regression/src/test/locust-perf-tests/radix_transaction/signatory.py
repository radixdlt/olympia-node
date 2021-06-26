import hashlib

import ecdsa
from ecdsa import SECP256k1
from ecdsa.util import sigencode_der


class Signatory():

    @staticmethod
    def sign( hash_to_sign, signatory_details):
        private_key_hex = signatory_details["private_key"]

        signing_key = ecdsa.SigningKey.from_string(bytearray.fromhex(private_key_hex), curve=SECP256k1,
                                                   hashfunc=hashlib.sha256)
        signed_hash = signing_key.sign_digest(bytearray.fromhex(hash_to_sign), sigencode=sigencode_der)

        return signed_hash
