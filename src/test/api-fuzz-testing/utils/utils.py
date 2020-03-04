import random
import string
def getID():
    return ''.join(random.choice(string.ascii_lowercase + string.digits) for _ in range(8))
