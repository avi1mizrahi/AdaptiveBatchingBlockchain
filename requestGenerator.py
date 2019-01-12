import http.client
import json
from time import sleep

conn = http.client.HTTPConnection("192.168.1.17:8020")

# payload = "{\n    \"amount\": 50\n}"
#
headers = {
    'content-type': "application/json"
}
#
#
# res = conn.getresponse()
# data = res.read()
# print(data.decode("utf-8"))
#
# j = json.loads(data)

def getAccountId(txidx):
    conn.request("GET", "/newAccounts/%s-%s-%s" % (txidx['serverId'],
                                                   txidx['blockIdx'],
                                                   txidx['txIdx']) , "", headers)
    res = conn.getresponse()
    data = res.read()
    if data:
        j = json.loads(data)
        if j:
            return j['id']


def createAccount():
    conn.request("POST", "/accounts" , "", headers)
    res = conn.getresponse()
    data = res.read()
    j = json.loads(data)

    id = None
    while not id:
        id = getAccountId(j)
        print('waiting')
        sleep(.2)

    print("Created ", id)
    return id


class NotReady(Exception):
    pass


def getTxStatus(txidx):
    conn.request("GET", "/txs/%s-%s-%s" % (txidx['serverId'],
                                           txidx['blockIdx'],
                                           txidx['txIdx']) , "", headers)
    res = conn.getresponse()
    data = res.read()
    if data:
        j = json.loads(data)
        if j:
            return j['isCommitted']
    raise NotReady()


def add(id, amount):
    payload = "{\n    \"amount\": %d\n}" % (amount, )
    conn.request("POST", "/accounts/%s/addAmount" % (id,) , payload, headers)

    res = conn.getresponse()
    data = res.read()
    j = json.loads(data)

    id = False
    while not id:
        try:
            id = getTxStatus(j)
            break
        except NotReady:
            print('waiting')
            sleep(.2)
    print()

def delete(id):
    conn.request("DELETE", "/accounts/%s" % (id,) , "", headers)

    res = conn.getresponse()
    data = res.read()
    j = json.loads(data)

    is_committed = False
    while not is_committed:
        try:
            is_committed = getTxStatus(j)
            break
        except NotReady:
            print('waiting')
            sleep(.2)
    print("Deleted: ", id)


acc = createAccount()
add(acc, 76)




