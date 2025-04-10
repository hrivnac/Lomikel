import numpy as np
import matplotlib.pyplot as plt
import os
import sys

cls = "CataclyV*"

if len(sys.argv) > 1:
  cls = sys.argv[1]

doPlot = False

fig, axes = plt.subplots(2, 1, figsize=(20, 20))

for fid, ax in zip(["1", "2"], axes):
  
  lst_file = f"../run/LightCurves/{cls}_{fid}.lst"
  jd_file = f"../run/LightCurves/{cls}_{fid}.jd"
  idx_file = f"../run/LightCurves/{cls}_{fid}.idx"
  
  if not (os.path.exists(lst_file) and os.path.exists(jd_file) and os.path.exists(idx_file)):
      ax.set_title(f"No data available for {cls}_{fid}")
      continue
  
  lst = np.loadtxt(lst_file) 
  jd  = np.loadtxt(jd_file)   
  idx = np.loadtxt(idx_file, dtype = str)   
  
  if lst.shape != jd.shape:
    raise ValueError("Mismatch in dimensions")
    
  for i in range(lst.shape[0]):
    mask = (jd[i] != 0) 
    filtered_jd  = jd[ i][mask]
    filtered_lst = lst[i][mask]
    if len(filtered_jd) > 0:
      ax.plot(filtered_jd, filtered_lst, label = idx[i])
  
  ax.set_xlabel("Julian Date")
  ax.set_ylabel("Values")
  ax.set_title("Light Curves of " + cls + "_" + fid)
  #ax.legend()
  
plt.savefig('LC-' + cls + '.png')

if doPlot:
  plt.show()
