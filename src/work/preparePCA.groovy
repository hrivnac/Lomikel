outputFile = new File("/tmp/PCA-sample.csv")
n_cls = 1000
n_objectId = 1000
min_jd = 1000
max_jd = 1000

classes = []
g.V().has('lbl', 'SourcesOfInterest').
      has('classifier', 'FINK_PORTAL').
      project('cls', 'size').
      by(values('cls')).
      by(outE().has('lbl', 'deepcontains').count()).
      order().
      by(select('size'), desc).
      limit(n_cls).
      toList().
      each {x -> classes += (x['cls'])}

outputFile.write("cls,objectId,jd_list\n")
classes.each {cls -> def results = g.V().has('lbl', 'SourcesOfInterest').
                                         has('classifier', 'FINK_PORTAL').
                                         has('cls', cls).
                                         outE().
                                         has('lbl', 'deepcontains').
                                         limit(n_objectId).
                                         project('objectId', 'jd').
                                         by(inV().
                                         values('objectId')).
                                         by(values('instances')).
                                         toList()
                     println(cls + ' -> ' + results.size())
                     results.each {row -> def objectId = row.get("objectId")
                                          def jdList = row.get("jd").split(',')
                                          def n = jdList.size()
                                          if (n >= min_jd) {
                                            if (n > max_jd) {
                                              jdList = jdList[0..(n_jd-1)]
                                              }
                                            outputFile.append("${cls},${objectId},${jdList.join(';').replaceAll(' ', '')}\n")
                                            }
                                    }
               }
    
