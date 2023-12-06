import sys

import jpype
import jpype.imports
from jpype import JImplements, JOverride, JImplementationFor

# ../dist/FinkBrowser.exe.jar
jpype.startJVM(jpype.getDefaultJVMPath(), "-ea", "-Djava.class.path=" + sys.argv[1], convertStrings=False)

from com.Lomikel.Januser                    import StringGremlinClient
from com.astrolabsoftware.FinkBrowser.Utils import Init

Init.init()

client = StringGremlinClient("134.158.74.85", 24445);

print(client.interpret("g.V().has('lbl', 'site').limit(2).next(2)"));
print(client.interpret2JSON("g.V().limit(20)"));
print(client.interpret2JSON("v=g.V().has('lbl', 'alert').limit(1).next();com.Lomikel.Januser.Wertex.enhance(v).properties()"));

client.close()

jpype.shutdownJVM()
