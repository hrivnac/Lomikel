import csv
import json
import os
import numpy as np
import matplotlib.pyplot as plt

doPlot = False
nout = 10
dirRun = '../run/LightCurves/lstm_data'
dirData = '../data/LightCurves/2024'

with open(dirData + '/iterator_config.json', 'r') as file:
  data = json.load(file)
  
maxclassValues = data['maxclassValues']  

numbers = []
directories = [dirRun + '/train/labels', dirRun + '/test/labels']
for directory in directories:
  for filename in os.listdir(directory):
    filepath = os.path.join(directory, filename)
    if os.path.isfile(filepath):
      with open(filepath, 'r') as f:
        try:
         number = int(f.read().strip())
         numbers.append(number)
        except ValueError:
          print(f"Skipping {filename}, not a valid integer")

numbers_array = np.array(numbers)

unique, counts = np.unique(numbers_array, return_counts=True)

sorted_indices = np.argsort(-counts)
top_numbers = unique[sorted_indices][:nout]
top_counts = counts[sorted_indices][:nout]

selection = []
print("Top {nout} most frequent numbers:")
for num, count in zip(top_numbers, top_counts):
 name = maxclassValues[num]
 print(f"{name}\t\t{num}:\t\t\t{count} times")
 selection += [name]
print(selection)

plt.bar(unique, counts)
plt.xlabel('Class')
plt.ylabel('Occurrences')
plt.title('Histogram of Class Occurrences')
plt.grid(True)

plt.tight_layout()
plt.savefig('Claseses.png')
  
if doPlot:
  plt.show()
