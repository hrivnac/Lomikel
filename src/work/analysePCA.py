import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt

types = '"SourcesOfInterest"'
classifier1 = '"FINK_PORTAL"'
classifier2 = '"FEATURES"'
limit = 50

df = pd.read_csv('overlaps.csv').\
        query('type1 == ' + types).\
        query('type2 == ' + types).\
        query('classifier1 == ' + classifier1).\
        query('classifier2 == ' + classifier2).\
        query('overlap > ' + str(limit))

plt.figure(figsize=(12, 6))
sns.scatterplot(
    data=df,      
    x='class1',    
    y='class2',    
    hue='overlap',   
    size='overlap',  
    sizes=(50, 500), 
    alpha=0.6,        
    palette='viridis')
plt.title('Scatterplot of ' + types)
plt.xlabel(classifier1)
plt.ylabel(classifier2)
plt.grid(True)
plt.legend(title='overlap')
plt.xticks(rotation=45)
plt.tight_layout()
plt.show()
