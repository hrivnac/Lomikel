import numpy as np
import matplotlib.pyplot as plt

cls = "Star"
fid   = "2"

lst = np.loadtxt("../data/" + cls + "_" + fid + ".lst") 
jd  = np.loadtxt("../data/" + cls + "_" + fid + ".jd")   
idx = np.loadtxt("../data/" + cls + "_" + fid + ".idx", dtype=str) 

if lst.shape != jd.shape:
  raise ValueError("Mismatch in dimensions")

plt.figure(figsize = (20, 10))
for i in range(lst.shape[0]):
    plt.plot(jd[i], lst[i], label = idx[i])

plt.xlabel("Julian Date")
plt.ylabel("Values")
plt.title("Light Curves of " + cls + "_" + fid)
plt.legend()
plt.savefig('LC-' + cls + "_" + fid + '.png')
plt.show()
