from gremlin_python import statics
from gremlin_python.process.anonymous_traversal import traversal
from gremlin_python.process.graph_traversal import __
from gremlin_python.process.strategies import *
from gremlin_python.driver import client
from gremlin_python.driver.driver_remote_connection import DriverRemoteConnection
from gremlin_python.process.traversal import T
from gremlin_python.process.traversal import Order
from gremlin_python.process.traversal import Cardinality
from gremlin_python.process.traversal import Column
from gremlin_python.process.traversal import Direction
from gremlin_python.process.traversal import Operator
from gremlin_python.process.traversal import P
from gremlin_python.process.traversal import Pop
from gremlin_python.process.traversal import Scope
from gremlin_python.process.traversal import Barrier
from gremlin_python.process.traversal import Bindings
from gremlin_python.process.traversal import WithOptions

statics.load_statics(globals())

client = client.Client('ws://134.158.74.85:24445/gremlin', 'g')

print(client.submit("g.V().has('lbl', 'source').has('objectId', 'ZTF18abimyys').out().has('lbl', 'alert').valueMap()").next())

print(client.submit("g.V().has('lbl', 'source').has('objectId', 'ZTF18abimyys').out().has('lbl', 'alert').elementMap()").next())

print(client.submit("g.V().has('lbl', 'source').has('objectId', 'ZTF18abimyys').out().has('lbl', 'alert').elementMap().unfold()").next())
        
print(client.submit("g.V().has('lbl', 'source').has('objectId', 'ZTF18abimyys').out().has('lbl', 'alert').out().has('lbl', 'candidate').valueMap()").next())

client.close()
