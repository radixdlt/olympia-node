import json
from random import randint, seed
import logging
SAMPLE_ATOM = "data/samples/atom.json"


def particle_group_to_big_size(ws_message: str, tokens_to_ignore: list) -> list:
    parsedMessage = json.loads(ws_message)
    particleGroups = parsedMessage["params"]["particleGroups"]
    for i in range(10):
        particleGroups.extend(particleGroups)

    parsedMessage["params"]["particleGroups"] = particleGroups
    json_string = json.dumps(parsedMessage)
    return [json_string]
