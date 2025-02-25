import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt
from tabulate import tabulate

classifier1 = 'FINK_PORTAL'
classifier2 = 'FEATURES'
#types = 'SourcesOfInterest'
types = 'AlertsOfInterest'
limit = 0.6

df = pd.read_csv('overlaps.csv')

df = df.query('type1 == @types').\
        query('type2 == @types').\
        query('classifier1 == @classifier1').\
        query('classifier2 == @classifier2')
        
df['normalized_overlap1'] = df.groupby('class1')['overlap'].\
                               transform(lambda x: x / x.sum())
df['normalized_overlap2'] = df.groupby('class2')['normalized_overlap1'].\
                               transform(lambda x: x / x.sum())
                               
df = df.query('normalized_overlap2 > @limit')                             
                                                
print(tabulate(df, headers = 'keys', tablefmt = 'psql'))

plt.figure(figsize=(12, 6))
sns.scatterplot(
    data=df,      
    x='class1',    
    y='class2',    
    hue='normalized_overlap2',   
    size='normalized_overlap2',  
    sizes=(0, 500), 
    alpha=0.6,        
    palette='viridis')
plt.title('Scatterplot of ' + types)
plt.xlabel(classifier2)
plt.ylabel(classifier1)
plt.grid(True)
plt.legend(title='overlap')
plt.xticks(rotation=45)
plt.tight_layout()
plt.show()
