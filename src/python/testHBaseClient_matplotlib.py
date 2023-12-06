import sys

import jpype
import jpype.imports
from jpype import JImplements, JOverride, JImplementationFor

import matplotlib.pyplot as plt

# ../dist/FinkBrowser.exe.jar
jpype.startJVM(jpype.getDefaultJVMPath(), "-ea", "-Djava.class.path=" + sys.argv[1], convertStrings=True)

from com.astrolabsoftware.FinkBrowser.HBaser import FinkHBaseClient
from com.astrolabsoftware.FinkBrowser.Utils  import Init

Init.init()

true  = jpype.java.lang.Boolean(True)
false = jpype.java.lang.Boolean(False)

#client = FinkHBaseClient("localhost", 2181)
client = FinkHBaseClient("134.158.74.54", 2181);
client.connect("ztf_season1", "schema_0.7.0_0.3.8")
#client.setLimit(10)

a17_x = [];
a17_y = [];
a19_x = [];
a19_y = [];
for r in client.scan("", "key:key:ZTF17", "i:ra,i:dec", 0, false, false).values():
  a17_x += [float(r['i:ra'])]
  a17_y += [float(r['i:dec'])]
for r in client.scan("", "key:key:ZTF19", "i:ra,i:dec", 0, false, false).values():
  a19_x += [float(r['i:ra'])]
  a19_y += [float(r['i:dec'])]

plt.plot(a17_x, a17_y, 'r.')
plt.plot(a19_x, a19_y, 'b.')
plt.title('ZTF17(red) + ZTF19(blue)')
plt.xlabel('ra')
plt.ylabel('dec')
plt.show()

client.close()

jpype.shutdownJVM()
