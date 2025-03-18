import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt
from tabulate import tabulate
import math

#-------------------------------------------------------------------------------

overlaps_dir = "13-45"

normalised = False          
    
merges = {"SOLAR":   ["Solar System candidate",
                      "Solar System MPC"]}

no_values = ["FC--1"]
                                  
classifiers1 = ['FEATURES',          'FEATURES',          'FINK_PORTAL',       'FEATURES',         'FEATURES',         'FINK_PORTAL'     ]
classifiers2 = ['FINK_PORTAL',       'FEATURES',          'FINK_PORTAL',       'FINK_PORTAL',      'FEATURES',         'FINK_PORTAL'     ]
types        = ['SourcesOfInterest', 'SourcesOfInterest', 'SourcesOfInterest', 'AlertsOfInterest', 'AlertsOfInterest', 'AlertsOfInterest']
limits_norm  = [0,                   0,                   0,                   0,                  0,                  0                 ]
limits_unorm = [0,                   0,                   0,                   0,                  0,                  0                 ]

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

overlaps_csv = "../data/Clusters/" + overlaps_dir + "/overlaps.csv"
df = pd.read_csv(overlaps_csv)

df = df[((df['classifier1'] != 'FINK_PORTAL') | (~df['class1'].isin(no_values))) &
        ((df['classifier2'] != 'FINK_PORTAL') | (~df['class2'].isin(no_values)))] 

for mrg in merges:
  df = merge(df, mrg, merges[mrg])

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
plt.savefig('Overlaps-' + overlaps_dir + "-" + name + '.png')
plt.show()
                                          
#print(tabulate(dfx, headers = 'keys', tablefmt = 'psql'))
