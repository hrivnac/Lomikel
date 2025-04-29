import csv
import numpy as np
import matplotlib.pyplot as plt

doPlot = False
dir = '../run/LightCurves/lstm_data'
filename = '../data/LightCurves/2024/all.csv'
set = 'train'
for n in range(0,10):
  
  jd  = np.loadtxt(dir + '/' + set + '/jds/jd_'       + str(n) + '.csv', delimiter=',') 
  seq = np.loadtxt(dir + '/' + set + '/features/seq_' + str(n) + '.csv', delimiter=',')
  
  with open(dir + '/' + set + '/oids/oid_' + str(n) + '.csv', 'r') as f:
    oid = f.readline().strip()
    
  with open(dir + '/' + set + '/labels/label_' + str(n) + '.csv', 'r') as f:
    clazz = ':' + f.readline().strip()

  print(oid)

  with open(filename, 'r') as f:
    reader = csv.DictReader(f)
    for row in reader:
      if row['objectId'] == oid:
        fids = np.array(row['collect_list(fid)'].split(';')).astype(int)
        mags = np.array(row['collect_list(magpsf)'].split(';')).astype(float)
        jds = np.array(row['collect_list(jd)'].split(';')).astype(float)       
        mask_fid1 = fids == 1
        mask_fid2 = fids == 2        
        jd_fid1 = jds[mask_fid1]
        mag_fid1 = mags[mask_fid1]        
        jd_fid2 = jds[mask_fid2]
        mag_fid2 = mags[mask_fid2]
        break
    
  title = oid + ':' + clazz
  
  fig, axes = plt.subplots(2, 1, figsize=(10, 6))  

  y1 = seq[:, 0]
  y2 = seq[:, 1]

  sorted_idx_fid1 = np.argsort(jd_fid1)
  sorted_idx_fid2 = np.argsort(jd_fid2)
  
  axes[0].plot(jd, y1, label='1', color='orange', marker='o')
  axes[0].plot(jd, y2, label='2', color='blue',   marker='o')
  axes[0].set_xlabel('JD')
  axes[0].set_ylabel('LC')
  axes[0].set_title(title)
  axes[0].legend()
  axes[0].grid(True)
  axes[1].plot(jd_fid1[sorted_idx_fid1], mag_fid1[sorted_idx_fid1], label='1', color='orange', marker='o')
  axes[1].plot(jd_fid2[sorted_idx_fid2], mag_fid2[sorted_idx_fid2], label='2', color='blue',   marker='o')
  axes[1].set_xlabel('JD')
  axes[1].set_ylabel('LC')
  axes[1].set_title(title)
  axes[1].legend()
  axes[1].grid(True)

  plt.tight_layout()
  plt.savefig(title + '.png')
  
  if doPlot:
    plt.show()
