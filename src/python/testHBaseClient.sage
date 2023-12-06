import sys

import jpype
import jpype.imports
from jpype import JImplements, JOverride, JImplementationFor

jpype.startJVM(jpype.getDefaultJVMPath(), "-ea", "-Djava.class.path=../dist/FinkBrowser.exe.jar", convertStrings=True)

import java.lang
import java.util

from com.astrolabsoftware.FinkBrowser.HBaser import FinkHBaseClient
from com.astrolabsoftware.FinkBrowser.Utils  import Init

Init.init()

true  = jpype.java.lang.Boolean(True)
false = jpype.java.lang.Boolean(False)

client = FinkHBaseClient("localhost", 2181)
client.connect("ztf_season1", "schema_0.7.0_0.3.8")
#client.setLimit(10);

a17 = [];
a19 = [];
for r in client.scan("", "key:key:ZTF17", "i:ra,i:dec", 100000, false, false).values():
  a17 += [(float(r['i:ra']), float(r['i:dec']))];
for r in client.scan("", "key:key:ZTF19", "i:ra,i:dec", 100000, false, false).values():
  a19 += [(float(r['i:ra']), float(r['i:dec']))];
p = list_plot(a17, color='red') + list_plot(a19, color='blue');
show(p, axes_labels = ('ra', 'dec'), title='ZTF17(red) + ZTF19(blue)');

client.close();

jpype.shutdownJVM()
