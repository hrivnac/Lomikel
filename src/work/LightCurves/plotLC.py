import numpy as np
import matplotlib.pyplot as plt

doPlot = True
n = '0'
dir = '../run/LightCurves/lstm_data'
set = 'train'

jd  = np.loadtxt(dir + '/' + set + '/jds/jd_'       + n + '.csv', delimiter=',') 
seq = np.loadtxt(dir + '/' + set + '/features/seq_' + n + '.csv', delimiter=',')

with open(dir + '/' + set + '/oids/oid_' + n + '.csv', 'r') as f:
  title = f.readline().strip()
  
with open(dir + '/' + set + '/labels/label_' + n + '.csv', 'r') as f:
  title += ' (' + f.readline().strip() + ')'

y1 = seq[:, 0]
y2 = seq[:, 1]

plt.figure(figsize=(10, 6))
plt.plot(jd, y1, label='1', marker='o')
plt.plot(jd, y2, label='2', marker='o')
plt.xlabel('JD (transformed)')
plt.ylabel('LC')
plt.title(title)
plt.legend()
plt.grid(True)
plt.tight_layout()

plt.savefig('LC_' + n + '.png')

if doPlot:
  plt.show()
