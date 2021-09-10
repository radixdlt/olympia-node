import json
import os

import ecdsa
import bech32
import requests

network = "sandpitnet"
number_of_accounts = 50
hrp = {
    "mainnet": "rdx",
    "stokenet": "tdx",
    "releasenet": "tdx3",
    "rcnet": "tdx4",
    "milestonenet": "tdx5",
    "devopsnet": "tdx6",
    "sandpitnet": "tdx7",
}


def generate_keys():
    generated_accounts = []
    for i in range(number_of_accounts):
        # Create the Private Key
        private_key = ecdsa.SigningKey.generate(curve=ecdsa.SECP256k1)
        private_key_bytes = private_key.to_string()
        print("Private Key: ", private_key_bytes.hex())

        # Generate Public Key from Private Key
        verifying_key = private_key.get_verifying_key()
        public_key_compressed_bytes = verifying_key.to_string("compressed")
        print("Public Key (Compressed): ", public_key_compressed_bytes.hex())

        # Convert Compressed Public Key into a Radix Engine Address
        readdr_bytes = b"\x04" + public_key_compressed_bytes

        # Convert Radix Engine Address to Bech32 Radix Wallet Address
        readdr_bytes5 = bech32.convertbits(readdr_bytes, 8, 5)
        wallet_address = bech32.bech32_encode(hrp[network], readdr_bytes5)
        print("Wallet Address: ", wallet_address)

        account = {
            "private_key": private_key_bytes.hex(),
            "public_key": public_key_compressed_bytes.hex(),
            "wallet_address": wallet_address
        }
        generated_accounts.append(account)
    return generated_accounts


accounts = generate_keys()


def get_epoch_validtors():
    global epoch_validators
    url = f"https://{network}.radixdlt.com/archive"
    payload = json.dumps({
        "jsonrpc": "2.0",
        "method": "validators.get_next_epoch_set",
        "params": {
            "size": 100,
            "cursor": "1"
        },
        "id": 1
    })
    headers = {
        'Content-Type': 'application/json'
    }
    return requests.request("POST", url, headers=headers, data=payload)


epoch_validators = get_epoch_validtors()

validators = []
for val in json.loads(epoch_validators.content)["result"]["validators"]:
    validators.append(val["address"])


def load_tokens(source_node, destinations, amount):
    import os
    for dest in destinations:
        url = f"https://{source_node['ip']}/account"

        payload = json.dumps({
            "jsonrpc": "2.0",
            "method": "account.submit_transaction_single_step",
            "params": {
                "actions": [
                    {
                        "type": "TokenTransfer",
                        "from": source_node["address"],
                        "to": dest["wallet_address"],
                        "amount": amount,
                        "rri": source_node["rri"]
                    }
                ]
            },
            "id": 1
        })
        headers = {
            'Authorization': f"Basic {os.getenv('BASIC_AUTH_CODE')}",
            'Content-Type': 'application/json'
        }

        response = requests.request("POST", url, headers=headers, data=payload, verify=False)

        print(f"{response.text}  for account {dest['wallet_address']}")


def get_native_token_rri(network_name):
    # url = f"https://{network_name}.radixdlt.com/archive"
    url = f"https://52.212.99.76/archive"

    payload = json.dumps({
        "jsonrpc": "2.0",
        "method": "tokens.get_native_token",
        "params": [],
        "id": 1
    })
    headers = {
        'Content-Type': 'application/json'
    }

    response = requests.request("POST", url, headers=headers, data=payload, verify=False)
    return json.loads(response.content)["result"]["rri"]


# source = {
#     "ip": os.getenv('SOURCE_NODE_IP'),
#     "address": os.getenv('SOURCE_NODE_ADDRESS'),
#     "rri": get_native_token_rri(network)
# }


source = {
    "ip": "52.212.75.120",
    "address": "tdx71qsp88qjm2smrmvzusc94nzu73cv8f4kkutlpux4x76w0s3gvngjr55s0zumm0",
    "rri": get_native_token_rri(network)
}
load_tokens(source, accounts, "1000000000000000000")

print(json.dumps(accounts))

print(json.dumps(validators))
