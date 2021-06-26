from locust import HttpUser, SequentialTaskSet, task, between

from client_api import archive_api
from client_api.dataobjects import RequestData
from radix_transaction.signatory import Signatory


class UserBehavior(SequentialTaskSet):

    @task()
    def get_transaction_history(self):
        account = archive_api.get_existing_entity(self.client, "accounts")
        payload = RequestData.get_transaction_history()
        payload["params"]["address"] = str(account["wallet_address"])
        archive_api.archive_endpoint_request(self.client, payload, f"transaction_history")

    @task()
    def stake_tokens(self):
        from_account = archive_api.get_existing_entity(self.client, "accounts")
        to_validator = archive_api.get_existing_entity(self.client, "validators")
        build_txn_payload = RequestData.build_transaction()
        action_stake_transaction = {
            "type": "StakeTokens",
            "from": str(from_account["wallet_address"]),
            "validator": str(to_validator),
            "amount": "11000000000000000000"
        }
        build_txn_payload["params"]["actions"].append(action_stake_transaction)
        build_txn_payload["params"]["feePayer"] = str(from_account["wallet_address"])
        build_response = archive_api.construction_endpoint_request(self.client, build_txn_payload, "build_stake_tokens")
        signed_hash_hex = Signatory.sign(build_response["result"]["transaction"]["hashOfBlobToSign"],
                                         from_account).hex()
        finalise_txn_payload = RequestData.finalise_transaction()
        blob = {
            "blob": build_response["result"]["transaction"]["blob"]
        }
        finalise_txn_payload["params"].append(blob)
        finalise_txn_payload["params"].append(signed_hash_hex)
        finalise_txn_payload["params"].append(from_account["public_key"])

        finalise_response = archive_api.construction_endpoint_request(self.client, finalise_txn_payload,
                                                                      "finalise_stake_tokens")
        submit_trx_payload = RequestData.submit_transaction()
        blob = {
            "blob": build_response["result"]["transaction"]["blob"]
        }

        submit_trx_payload["params"].append(blob)
        submit_trx_payload["params"].append(signed_hash_hex)
        submit_trx_payload["params"].append(from_account["public_key"])
        submit_trx_payload["params"].append(finalise_response["result"]["txID"])

        finalise_response = archive_api.construction_endpoint_request(self.client, submit_trx_payload,
                                                                      "submit_stake_tokens")
        print(f" Txn {finalise_response['result']['txID']} successfully staked by wallet {from_account['wallet_address']} to validator {to_validator}")
