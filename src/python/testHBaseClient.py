import sys

import jpype
import jpype.imports
from jpype import JImplements, JOverride, JImplementationFor

# ../dist/FinkBrowser.exe.jar
jpype.startJVM(jpype.getDefaultJVMPath(), "-ea", "-Djava.class.path=" + sys.argv[1], convertStrings=False)

from com.astrolabsoftware.FinkBrowser.HBaser import FinkHBaseClient
from com.astrolabsoftware.FinkBrowser.Utils  import Init

Init.init()

true  = jpype.java.lang.Boolean(True)
false = jpype.java.lang.Boolean(False)

#client = FinkHBaseClient("localhost", 2181)
client = FinkHBaseClient("134.158.74.54", 2181);
client.connect("ztf_season1", "schema_0.7.0_0.3.8")
client.setLimit(10)

print(client.scan(None, "key:key:ZTF17", None, 100000, true, true)) 

client.close()

jpype.shutdownJVM()

