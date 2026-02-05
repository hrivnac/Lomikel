import numpy as np
import csv

data = np.load("../data/LightCurves/Latent/src/latents_with_metadata.npz")

latent_encodings = data["latent_encodings"]
objectIds = data["objectIds"]
classes = data["classes"]

print(objectIds.size, classes[4], latent_encodings.size)

with open('../data/LightCurves/Latent/all.csv', 'w', newline='') as out_file:
  writer = csv.writer(out_file)
  writer.writerow(('objectId', 'collect_list(fid)', 'collect_list(magpsf)', 'collect_list(jd)', 'collect_list(class)', 'maxclass'))
  for n in range(0, 3606):
    oid = objectIds[n]
    maxclass = classes[n]
    fids = '1;2;1;2;1;2;1;2;1;2;1;2;1;2;1;2'
    magpsfs = ''
    clsses = ''
    fids = ''
    jds = ''
    jd = 1
    first = True
    for i in range(0, 8):
      if first:
        first = False
      else:
        magpsfs += ';'
        clsses += ';'
        fids += ';'
        jds += ';'
      m1 = str(latent_encodings[n][i][0])
      m2 = str(latent_encodings[n][i][1])
      if m1 == 'nan':
        m1 = '0'
      if m2 == 'nan':
        m2 = '0'
      magpsfs += m1 + ';' + m2
      clsses += maxclass + ';' + maxclass
      fids += '1;2'
      jds += str(jd) + ';' + str(jd)
      jd += 1
    writer.writerow((oid, fids, magpsfs, jds, clsses, maxclass))
