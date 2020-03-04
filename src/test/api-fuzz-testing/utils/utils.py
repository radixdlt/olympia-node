import random
import string
def getID():
    _id = ''.join(random.choice(string.ascii_uppercase + string.digits) for _ in range(8))
    return _id.lower()

