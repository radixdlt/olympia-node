import json
from random import randint

from client_api import endpoints
from client_api.dataobjects import RequestData
from client_api.datapool import DataPool
from helpers import post_headers, logOnError
from radix_transaction.signatory import Signatory

with open('client_api/data.json', "r") as f:
    data = json.load(f)

testData = DataPool(data)


def get_existing_entity(entity):
    return testData[entity][randint(0, len(testData[entity]) - 1)]


def archive_endpoint_request(client, payload, name):
    archive_endpoint = endpoints.get_archive_endpoint()
    return post_rpc_request(client, name, payload, archive_endpoint)


def post_rpc_request(client, name, payload, endpoint):
    with client.request("POST",
                        endpoint,
                        name=name,
                        data=json.dumps(payload),
                        headers=post_headers(),
                        catch_response=True, verify=False) as response:
        if response.status_code == 200:
            response_json = json.loads(response.text)
            if "error" not in response_json:
                return json.loads(response.text)
            else:
                print(f"Error response {response_json}")
        else:
            logOnError(response)


def construction_endpoint_request(client, payload, name):
    construction_endpoint = endpoints.get_construction_endpoint()
    return post_rpc_request(client, name, payload, construction_endpoint)


def get_transaction_history_method(client, account):
    payload = RequestData.get_transaction_history()
    payload["params"]["address"] = str(account["wallet_address"])
    archive_endpoint_request(client, payload, f"transaction_history")


def stake_tokens_method(client, from_account, to_validator):
    build_txn_payload = RequestData.build_transaction()
    action_stake_transaction = {
        "type": "StakeTokens",
        "from": str(from_account["wallet_address"]),
        "validator": str(to_validator),
        "amount": "11000000000000000000"
    }
    build_txn_payload["params"]["actions"].append(action_stake_transaction)
    build_txn_payload["params"]["feePayer"] = str(from_account["wallet_address"])
    build_response = construction_endpoint_request(client, build_txn_payload,
                                                   f"build_stake_tokens")
    build_response, finalise_response, signed_hash_hex = finalize_transaction_method(build_txn_payload, client,
                                                                                     from_account, build_response)
    finalise_response = submit_transaction_method(build_response, client, finalise_response, from_account,
                                                  signed_hash_hex)
    print(
        f" Txn {finalise_response['result']['txID']} successfully staked by wallet {from_account['wallet_address']} to validator {to_validator}")


def transfer_tokens_method(client, from_account, to_account):
    build_txn_payload = RequestData.build_transaction()
    action_tokentransfer_transaction = {
        "type": "TokenTransfer",
        "from": str(from_account["wallet_address"]),
        "to": str(to_account["wallet_address"]),
        "amount": "1100000000000000000",
        "rri": "xrd_rb1qya85pwq"
    }
    build_txn_payload["params"]["actions"].append(action_tokentransfer_transaction)
    build_txn_payload["params"]["feePayer"] = str(from_account["wallet_address"])
    build_response = construction_endpoint_request(client, build_txn_payload,
                                                   f"build_transfer_tokens")

    build_response, finalise_response, signed_hash_hex = finalize_transaction_method(build_txn_payload, client,
                                                                                     from_account, build_response)
    finalise_response = submit_transaction_method(build_response, client, finalise_response, from_account,
                                                  signed_hash_hex)
    print(
        f" Txn {finalise_response['result']['txID']} successfully transferred tokens from  wallet {from_account['wallet_address']} to  {to_account['wallet_address']}")
    return finalise_response['result']['txID']


def submit_transaction_method(build_response, client, finalise_response, from_account, signed_hash_hex):
    submit_trx_payload = RequestData.submit_transaction()
    blob = {
        "blob": build_response["result"]["transaction"]["blob"]
    }
    submit_trx_payload["params"].append(blob)
    submit_trx_payload["params"].append(signed_hash_hex)
    submit_trx_payload["params"].append(from_account["public_key"])
    submit_trx_payload["params"].append(finalise_response["result"]["txID"])
    return construction_endpoint_request(client, submit_trx_payload,
                                         "submit_transaction")


def finalize_transaction_method(build_txn_payload, client, from_account, build_response):
    signed_hash_hex = Signatory.sign(build_response["result"]["transaction"]["hashOfBlobToSign"],
                                     from_account).hex()
    finalise_txn_payload = RequestData.finalise_transaction()
    blob = {
        "blob": build_response["result"]["transaction"]["blob"]
    }
    finalise_txn_payload["params"].append(blob)
    finalise_txn_payload["params"].append(signed_hash_hex)
    finalise_txn_payload["params"].append(from_account["public_key"])
    finalise_response = construction_endpoint_request(client, finalise_txn_payload,
                                                      "finalise_transaction")
    return build_response, finalise_response, signed_hash_hex
