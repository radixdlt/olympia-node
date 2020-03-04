import json
from random import randint, seed
import logging
SAMPLE_ATOM = "data/samples/atom.json"


def create_fuzzed_particle_group(ws_message, ignore_tokens):
    parsedMessage = json.loads(ws_message)
    particleGroups = parsedMessage["params"]["particleGroups"]
    for i in range(7):
        particleGroups.extend(particleGroups)

    parsedMessage["params"]["particleGroups"] = particleGroups
    json_string = json.dumps(parsedMessage)
    return [json_string]
