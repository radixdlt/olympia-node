import json
from random import randint

from client_api import endpoints
from client_api.datapool import DataPool
from helpers import post_headers, logOnError

testData = DataPool()


def get_existing_entity(client, entity):
    for key, value in testData.__class__.__dict__.items():
        datapoolEntities = ['accounts']

        if key == entity and key in datapoolEntities:
            if len(value) != 0:
                return value[randint(0, len(value) - 1)]


def get_transaction_history(client, payload, name):
    archive_endpoint = endpoints.get_archive_endpoint()
    with client.request("POST",
                        archive_endpoint,
                        name=name,
                        data=json.dumps(payload),
                        headers=post_headers(),
                        catch_response=True, verify=False) as response:
        if response.status_code != 200:
            logOnError(response)
