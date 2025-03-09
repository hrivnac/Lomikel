import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt
from tabulate import tabulate
import math
           
normalised = True          
                                      
classifiers1 = ['FEATURES',          'FEATURES',          'FINK_PORTAL',       'FEATURES',         'FEATURES',         'FINK_PORTAL'     ]
classifiers2 = ['FINK_PORTAL',       'FEATURES',          'FINK_PORTAL',       'FINK_PORTAL',      'FEATURES',         'FINK_PORTAL'     ]
types        = ['SourcesOfInterest', 'SourcesOfInterest', 'SourcesOfInterest', 'AlertsOfInterest', 'AlertsOfInterest', 'AlertsOfInterest']
limits_norm  = [4,                   0,                   0,                   10,                 0,                  0                 ]
limits_unorm = [0,                   0,                   0,                   0,                  0,                  0                 ]

if normalised:
  name    = 'normalised'
  limits  = limits_norm
  overlap = 'normalized_overlap'
else:
  name    = 'full'
  limits  = limits_unorm
  overlap = 'overlap'

#sample = pd.read_csv('PCA-sample.csv')
#cls_values = sample['cls'].unique()
#print(cls_values)

df = pd.read_csv('overlaps.csv')
#df = df[((df['classifier1'] != 'FINK_PORTAL') | (df['class1'].isin(cls_values))) &
#        ((df['classifier2'] != 'FINK_PORTAL') | (df['class2'].isin(cls_values)))] 
df = df[((df['classifier1'] != 'FEATURES'   ) | (df['class1'] != "FC--1"      )) &
        ((df['classifier2'] != 'FEATURES'   ) | (df['class2'] != "FC--1"      ))]

fig, axes = plt.subplots(2, 3, figsize = (20, 15))  

for i, ax in enumerate(axes.flat): 
  classifier1 = classifiers1[i] 
  classifier2 = classifiers2[i]
  type = types[i]
  limit = limits[i] 
  dfx = df.query('type1 == @type').\
           query('type2 == @type').\
           query('classifier1 == @classifier1').\
           query('classifier2 == @classifier2')
  dfx['normalized_overlap'] = dfx.groupby(['class1', 'class2'])['overlap'].\
                                  transform(lambda x: x / math.sqrt(x.sum()))

  dfx = dfx.query(overlap + ' > @limit')                              
  sns.scatterplot(data    = dfx,      
                  x       = 'class1',    
                  y       = 'class2',    
                  hue     = overlap,   
                  size    = overlap,  
                  sizes   = (0, 500), 
                  alpha   = 0.6,        
                  palette = 'viridis',
                  ax      = ax)
  ax.set_title(type + ' of ' + classifier1 + ' * ' + classifier2)
  ax.set_xlabel(classifiers1[i])
  ax.set_ylabel(classifiers2[i])
  ax.grid(True)
  ax.legend(title = 'overlap')
  ax.tick_params(axis = 'x', rotation = 45)
  #print(i)
  #print(tabulate(dfx, headers = 'keys', tablefmt = 'psql'))
    
    
plt.tight_layout()
plt.savefig('Overlaps-' + name + '.png')
plt.show()
                                          
#print(tabulate(dfx, headers = 'keys', tablefmt = 'psql'))
