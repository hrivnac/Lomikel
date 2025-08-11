import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt
from tabulate import tabulate
import math

#-------------------------------------------------------------------------------

normalised = False          

merges = {}
no_values = []                     
   
#no_values = ["FC--1"]
        
features_flavor = 'Clusters/2025/13-50'                                
classifiers1 = ['FEATURES',      'FEATURES',      'FINK', 'FEATURES',      'FEATURES',      'XMATCH']
classifiers2 = ['FINK',          'FEATURES',      'FINK', 'XMATCH',        'FEATURES',      'XMATCH']
flavors1     = [features_flavor, features_flavor, '',     features_flavor, features_flavor, ''      ]
flavors2     = ['',              features_flavor, '',     '',              features_flavor, ''      ]
limits_norm  = [0,               0,               0,      10000,           10000,           10000     ]
limits_unorm = [0,               0,               0,      10000,           10000,           10000     ]

#-------------------------------------------------------------------------------

def merge4Class(df, class_col, result_col, merged_cols):
   mask = df[class_col].isin(merged_cols)
   grouped = df[mask].groupby([col for col in df.columns if col not in [class_col, "overlap"]], as_index = False).\
                      agg({"overlap": "sum"})
   grouped[class_col] = result_col
   df = pd.concat([df[~mask], grouped], ignore_index = True)
   return df 
   
def merge(df, result_col, merged_cols):
  df = merge4Class(df, "class1", result_col, merged_cols)
  df = merge4Class(df, "class2", result_col, merged_cols)
  return df
        
#-------------------------------------------------------------------------------
        
if normalised:
  name    = 'normalised'
  limits  = limits_norm
  overlap = 'normalized_overlap'
else:
  name    = 'full'
  limits  = limits_unorm
  overlap = 'overlap'

overlaps_csv = "overlaps.csv"
df = pd.read_csv(overlaps_csv, keep_default_na=False, na_values=[])

df = df[((df['classifier1'] != 'FINK') | (~df['class1'].isin(no_values))) &
        ((df['classifier2'] != 'FINK') | (~df['class2'].isin(no_values)))] 

for mrg in merges:
  df = merge(df, mrg, merges[mrg])

fig, axes = plt.subplots(2, 3, figsize = (20, 15))  

for i, ax in enumerate(axes.flat): 
  classifier1 = classifiers1[i] 
  classifier2 = classifiers2[i]
  flavor1 = flavors1[i]
  flavor2 = flavors2[i]
  limit = limits[i] 
  dfx = df.query('flavor1 == @flavor1').\
           query('flavor2 == @flavor2').\
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
  ax.set_title(classifier1 + '=' + flavor1 + ' * ' + classifier2 + '=' + flavor2)
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
