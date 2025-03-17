import numpy as np
import matplotlib.pyplot as plt

cls = "RotV*"
fid   = "2"

lst = np.loadtxt("../run/" + cls + "_" + fid + ".lst") 
jd  = np.loadtxt("../run/" + cls + "_" + fid + ".jd")   
idx = np.loadtxt("../run/" + cls + "_" + fid + ".idx", dtype=str) 

if lst.shape != jd.shape:
  raise ValueError("Mismatch in dimensions")

plt.figure(figsize = (20, 10))
for i in range(lst.shape[0]):
  mask = (jd[i] != 0) 
  filtered_jd  = jd[ i][mask]
  filtered_lst = lst[i][mask]
  if len(filtered_jd) > 0:
    plt.plot(filtered_jd, filtered_lst, label = idx[i])

plt.xlabel("Julian Date")
plt.ylabel("Values")
plt.title("Light Curves of " + cls + "_" + fid)
plt.legend()
plt.savefig('LC-' + cls + "_" + fid + '.png')
plt.show()
