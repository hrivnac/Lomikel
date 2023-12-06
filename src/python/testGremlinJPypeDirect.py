import sys

import jpype
import jpype.imports
from jpype import JImplements, JOverride, JImplementationFor

# ../dist/FinkBrowser.exe.jar
jpype.startJVM(jpype.getDefaultJVMPath(), "-ea", "-Djava.class.path=" + sys.argv[1], convertStrings=False)

from com.Lomikel.Januser                    import DirectGremlinClient
from com.astrolabsoftware.FinkBrowser.Utils import Init

Init.init()

client = DirectGremlinClient("134.158.74.85", 24445);
g = client.g();

t = g.V().has('lbl', 'alert').limit(4).values('rowkey');
print(client.submit(t).all().get().get(0).getString());

s = "v=g.V().has('lbl', 'alert').limit(1).next();h=com.Lomikel.Januser.Wertex.enhance(v);h.properties();h.property('i:simag2').toString()";
print(client.submit(s).one().getString());

client.close()

jpype.shutdownJVM()
