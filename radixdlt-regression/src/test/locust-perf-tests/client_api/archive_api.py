import json
from random import randint

from client_api import endpoints
from client_api.datapool import DataPool
from helpers import post_headers, logOnError

testData = DataPool()


def get_existing_entity(client, entity):
    for key, value in testData.__class__.__dict__.items():
        datapoolEntities = ['accounts', 'validators']

        if key == entity and key in datapoolEntities:
            if len(value) != 0:
                return value[randint(0, len(value) - 1)]


def archive_endpoint_request(client, payload, name):
    archive_endpoint = endpoints.get_archive_endpoint()
    return post_rpc_request(client, name, payload,archive_endpoint)


def post_rpc_request(client, name, payload, endpoint):
    with client.request("POST",
                        endpoint,
                        name=name,
                        data=json.dumps(payload),
                        headers=post_headers(),
                        catch_response=True, verify=False) as response:
        if response.status_code == 200:
            return json.loads(response.text)
        else:
            logOnError(response)


def construction_endpoint_request(client, payload, name):
    construction_endpoint = endpoints.get_construction_endpoint()
    return post_rpc_request(client,name,payload,construction_endpoint)

