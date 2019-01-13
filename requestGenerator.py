import http.client
import json
from time import sleep


# payload = "{\n    \"amount\": 50\n}"
#
#
#
# res = self.conn.getresponse()
# data = res.read()
# print(data.decode("utf-8"))
#
# j = json.loads(data)


class Client:
    def __init__(self, addr):
        self.conn = http.client.HTTPConnection(addr)
        self.headers = {
            'content-type': "application/json"
        }

    def getAccountId(self, txidx):
        self.conn.request("GET", "/newAccounts/%s-%s-%s" % (txidx['serverId'],
                                                       txidx['blockIdx'],
                                                       txidx['txIdx']) , "", self.headers)
        res = self.conn.getresponse()
        data = res.read()
        if data:
            j = json.loads(data)
            if j:
                return j['id']
    
    def createAccount(self):
        self.conn.request("POST", "/accounts" , "", self.headers)
        res = self.conn.getresponse()
        data = res.read()
        j = json.loads(data)
    
        id = None
        while not id:
            id = self.getAccountId(j)
            print('waiting')
            sleep(.2)
    
        print("Created ", id)
        return id

    class NotReady(Exception):
        pass
    
    def getAmount(self, id):
        self.conn.request("GET", "/accounts/%s/amount" % (id,), "", self.headers)
        res = self.conn.getresponse()
        data = res.read()
        if data:
            j = json.loads(data)
            if j:
                return j['amount']
        raise self.NotReady()
    
    def getTxStatus(self, txidx):
        self.conn.request("GET", "/txs/%s-%s-%s" % (txidx['serverId'],
                                               txidx['blockIdx'],
                                               txidx['txIdx']), "", self.headers)
        res = self.conn.getresponse()
        data = res.read()
        if data:
            j = json.loads(data)
            if j:
                return j['isCommitted']
        raise self.NotReady()
    
    def transfer(self, fromid, toid, amount):
        payload = {'from': {'id': fromid}, 'to': {'id': toid}, 'amount': amount}
        self.conn.request("POST", "/transfers/", json.dumps(payload), self.headers)
    
        res = self.conn.getresponse()
        data = res.read()
        j = json.loads(data)
    
        id = False
        while not id:
            try:
                id = self.getTxStatus(j)
                break
            except self.NotReady:
                print('waiting')
                sleep(.2)
        print("transferred")
    
    def add(self, id, amount):
        payload = "{\n    \"amount\": %d\n}" % (amount, )
        self.conn.request("POST", "/accounts/%s/addAmount" % (id,) , payload, self.headers)
    
        res = self.conn.getresponse()
        data = res.read()
        j = json.loads(data)
    
        id = False
        while not id:
            try:
                id = self.getTxStatus(j)
                break
            except self.NotReady:
                print('waiting')
                sleep(.2)
        print("added")
    
    def delete(self, id):
        self.conn.request("DELETE", "/accounts/%s" % (id,) , "", self.self.headers)
    
        res = self.conn.getresponse()
        data = res.read()
        j = json.loads(data)
    
        is_committed = False
        while not is_committed:
            try:
                is_committed = self.getTxStatus(j)
                break
            except self.NotReady:
                print('waiting')
                sleep(.2)
        print("Deleted: ", id)


c1 = Client("127.0.0.1:8010")
c2 = Client("132.68.47.109:8020")

acc11 = c1.createAccount()
acc12 = c1.createAccount()

acc21 = c2.createAccount()

c1.add(acc21, 100)
c1.add(acc11, 100)
c2.add(acc12, 700)

c1.transfer(acc11, acc12, 70)
c2.transfer(acc11, acc12, 70)
c2.transfer(acc21, acc12, 70)

print("c11 amount = ", c1.getAmount(acc11))
print("c11 amount = ", c2.getAmount(acc11))

print("c12 amount = ", c1.getAmount(acc12))
print("c12 amount = ", c2.getAmount(acc12))

print("c21 amount = ", c1.getAmount(acc21))
print("c21 amount = ", c2.getAmount(acc21))

c1.delete(acc11)
c2.delete(acc12)
c1.delete(acc11)
c2.delete(acc21)
