# pip install janusgraphpython

from gremlin_python.process.anonymous_traversal import traversal
from gremlin_python.driver.driver_remote_connection import DriverRemoteConnection
from janusgraph_python.driver.serializer import JanusGraphSONSerializersV3d0

connection = DriverRemoteConnection(
    'ws://134.158.243.144:24444/gremlin',
    'g',
    message_serializer=JanusGraphSONSerializersV3d0()
)

g = traversal().withRemote(connection)

g.addV('NewTag') \
 .property('lbl', 'NewTag') \
 .property('objectId', '313756671556976691') \
 .property('cls', 'rubin.tag_extragalactic_new_candidate') \
 .iterate()

connection.close()
