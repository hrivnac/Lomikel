def globals = [:]

globals << [hook : [
  onStartUp: { ctx -> ctx.logger.info("Executed once at startup of Gremlin Server.")},
  onShutDown: { ctx -> ctx.logger.info("Executed once at shutdown of Gremlin Server.")}
  ] as LifeCycleHook]
  
// -----------------------------------------------------------------------------
  
class LomikelServer {

  def static init() {
    println "class Lomikel Server initialised"
    }

  def static hi() {
    return "Hello World from Lomikel Server !"
    }
    
  def static geosearch(ra, dec, ang, jdmin, jdmax, limit) {
    def lat = dec
    def lon = ra - 180
    def dist = ang * 6371.0087714 * Math.PI / 180
    def nDir = g.V().has('direction', geoWithin(Geoshape.circle(lat, lon, dist))).count().next()
    def nJD  = g.V().has('direction', geoWithin(Geoshape.circle(lat, lon, dist))).limit(nDir).has('jd', inside(jdmin, jdmax)).count().next()
    if (limit < nJD) {
      nJD = limit
      }
    return g.V().has('direction', geoWithin(Geoshape.circle(lat, lon, dist))).limit(nDir).has('jd', inside(jdmin, jdmax)).limit(nJD)
    }
  
  def static get_or_create(lbl, name, value) {
    return g.V().has('lbl', lbl).
                 has(name, value).
                 fold().
                 coalesce(unfold(), addV(lbl).
                                    property('lbl', lbl).
                                    property(name, value));
    }
    
  def static get_or_create_edge(lbl1, name1, value1, lbl2, name2, value2, edge) {
    return g.V().has('lbl', lbl1).
                 has(name1, value1).
                 as('v').
             V().has('lbl', lbl2).
                 has(name2, value2).
             coalesce(__.inE(edge).where(outV().as('v')), addE(edge).from('v'));
      }

  // w = g.addV().property('lbl', 'datalink').property('technology', 'Phoenix').property('url', 'jdbc:phoenix:ithdp2101.cern.ch:2181' ).property('query', "select * from AEI.CANONICAL_0 where project = 'mc16_13TeV'").next()
  // w = g.addV().property('lbl', 'datalink').property('technology', 'Graph'  ).property('url', 'hbase:188.184.87.217:8182:janusgraph').property('query', "g.V().limit(1)").next()
  // w = g.addV().property('lbl', 'datalink').property('technology', 'HBase'  ).property('url', '134.158.74.54:2183:ztf:schema').property('query', "client.setLimit(10); return client.scan(null, null, null, 0, false, false)").next()
  def static getDataLink(v, q = null) {
    def url   = v.values('url').next()
    def query
    if (q != null) {
      query = q
      }
    else if (v.values('query').hasNext()) {
      query = v.values('query').next()
      }
    else {
      return 'no Query'
      }
    try {
      switch (v.values('technology').next()) {
        case 'HBase':
          def (hostname, port, table, schema) = url.split(':') // 134.158.74.54:2181:ztf:schema_0.7.0_0.3.8
          def client = new com.Lomikel.HBaser.HBaseClient(hostname, port)
          client.connect(table, schema)
          return Eval.me('client', client, query)
          break
        case 'Graph':
          def (backend, hostname, port, table) = url.split(':') // hbase:188.184.87.217:8182:janusgraph
          def graph = JanusGraphFactory.build().
                                        set('storage.backend',     backend ).
                                        set('storage.hostname',    hostname).
                                        set('storage.port',        port    ).
                                        set('storage.hbase.table', table   ).
                                        open()
          def g = graph.traversal()
          query = query
          return Eval.me('g', g, query)
          break
        case 'Phoenix':
          return groovy.sql.Sql.newInstance(url, 'org.apache.phoenix.jdbc.PhoenixDriver').
                                rows(query).toString()
          break
        default:
          return 'DataLink ' + v + ' unknown'
          }
        }
      catch (Exception e) {
      return 'DataLink ' + v + ' not found'
      }
    }

  def static standardDeviation(lbl, variableNames) {
    def sdMap = [:]
    variableNames.split().stream().each {v ->
      g.V().has('lbl', lbl).values(v).fold().as(v).mean(local).as('mean').select(v).unfold().math('(_-mean)^2').mean().math('sqrt(_)').map{sd -> println  v + ': ' + sd
                                                                                                                        sdMap += [(v):sd]}.next()
      }
    return sdMap
    }
      
  def static dropV(label, n) {
    def g = graph.traversal()
    def m = g.V().has('lbl', label).count().next()
    while (m > 0) {
      println('' + m + ' ' + label + 's to drop')
      g.V().has('lbl', label).limit(n).drop().iterate()
      graph.traversal().tx().commit()
      m -= n
      }
    }
    
   def static dropE(label, n) {
    def g = graph.traversal()
    def m = g.E().has('lbl', label).count().next()
    while (m > 0) {
      println('' + m + ' ' + label + 's to drop')
      g.E().has('lbl', label).limit(n).drop().iterate()
      graph.traversal().tx().commit()
      m -= n
      }
    }
    
  def static drop_by_date(importDate, nCommit, tWait) {
    def g = graph.traversal()
    //importDate = 'Mon Feb 14 05:51:20 UTC 2022'
    //nCommit = 500
    def i = 0
    def tot = 0
    def nMax = g.V().has('importDate', importDate).count().next()
    println('' + nMax + ' vertexes to drop')
    def t0 = System.currentTimeMillis()
    while(true) {
      g.V().has('importDate', importDate).limit(nCommit).out().out().drop().iterate()
      g.V().has('importDate', importDate).limit(nCommit).out().drop().iterate()
      g.V().has('importDate', importDate).limit(nCommit).drop().iterate()
      graph.traversal().tx().commit()
      Thread.sleep(tWait)
      tot = nCommit * ++i
      def dt = (System.currentTimeMillis() - t0) / 1000
      def per = 100 * tot / nMax
      def freq = tot / dt
      def rest = (nMax - tot) / freq / 60 /60
      println(tot + ' = ' + per + '% at ' + freq + 'Hz, ' + rest + 'h to go')
      }
    }
      
  def static importStatus() {
    def txt = ''
    txt += 'Imported:\n'
    g.V().has('lbl', 'Import').has('nAlerts', neq(0)).order().by('importSource').valueMap('importSource', 'importDate', 'nAlerts').each{txt += '\t' + it + '\n'}
    txt += 'Importing:\n'
    g.V().has('lbl', 'Import').hasNot('complete').order().by('importSource').valueMap('importSource', 'importDate').each{txt += '\t' + it + '\n'}
    return txt
    }
    
  def static candidates(objectId) {
    return g.V().has('objectId', objectId).out().out().has('lbl', 'candidate')
    }  
    
  def static findPCAClusters(edgeName, edgeValue, minSize, maxDist) {
    def clusters = []
    def ids = []
    def sumClusters = 0
    def i
    def clusterPCA
    g.V().has('lbl', 'PCA').each {v -> i = v.id()
                                       if (!ids.contains(i)) {
                                         clusterPCA = g.V(i).emit().repeat(union(outE(edgeName).has(edgeValue, lt(maxDist)).inV(),
                                                                                 inE( edgeName).has(edgeValue, lt(maxDist)).outV()).dedup()).toSet().sort()
                                         if (clusterPCA.size() >= minSize) {
                                           def cluster = []
                                           for (pca in clusterPCA) {
                                             ids += [pca.id()]
                                             cluster += [g.V(pca.id()).in().has('lbl', 'source').values('objectId').next()]
                                             }
                                           clusters += [cluster]
                                           sumClusters += cluster.size()
                                           println '+ ' + cluster.size()
                                           }
                                         }
                                       }
    println '' + sumClusters + ' sources in ' + clusters.size()  + ' clusters out of ' + g.V().has('lbl', 'PCA').count().next() + ' PCAs' 
    return clusters
    }
    
  def static registerAlertOfInterest(alertType, objectId, jd, url) {   
    def aoi = g.V().has('AlertsOfInterest', 'lbl', 'AlertsOfInterest').
                    has('alertType', alertType).
                    fold().
                    coalesce(unfold(), 
                             addV('AlertsOfInterest').
                             property('lbl',        'AlertsOfInterest').
                             property('alertType',  alertType         ).
                             property('technology', 'HBase'           ).
                             property('url',         url              )).
                    next();
    def a = g.V().has('alert', 'lbl', 'alert').
                  has('objectId', objectId).
                  has('jd',       jd).
                  fold().
                  coalesce(unfold(), 
                           addV('alert').
                           property('lbl',     'alert'  ).
                           property('objectId', objectId).
                           property('jd',       jd      )).
                  next();
    g.V(aoi).addE('contains').
             to(__.V(a)).
             property('lbl', 'contains').
             iterate();      
    graph.tx().commit();
    }
      
  def static registerSourcesOfInterest(sourceType, objectId, weight, instances, url) {   
    def soi = g.V().has('SourcesOfInterest', 'lbl', 'SourcesOfInterest').
                    has('sourceType', sourceType).
                    fold().
                    coalesce(unfold(), 
                             addV('SourcesOfInterest').
                             property('lbl',        'SourcesOfInterest').
                             property('sourceType', sourceType         ).
                             property('technology', 'HBase'            ).
                             property('url',        url                )).
                    next();
    def s = g.V().has('source', 'lbl', 'source').
                  has('objectId', objectId).
                  fold().
                  coalesce(unfold(), 
                           addV('source').
                           property('lbl',      'source').
                           property('objectId', objectId)).
                  next();
    g.V(soi).addE('contains').
             to(__.V(s)).
             property('lbl',       'contains').
             property('weight',    weight    ).
             property('instances', instances ).
             iterate();
    graph.tx().commit();
    }
      
  def static exportAlertsOfInterest(alertType, fn) {  
    g.V().has('lbl', 'AlertsOfInterest').
          has('alertType', alertType).
          repeat(__.outE().
                    subgraph('subGraph').
                    inV()).
          until(outE().
                count().
                is(0)).
          cap('subGraph').
          next().
          io(IoCore.graphml()).
          writeGraph(fn)
    }
       
  def static exportSourcesOfInterest(fn) {  
    g.V().has('lbl', 'SourceOfInterest').
          repeat(__.outE().
                    subgraph('subGraph').
                    inV()).
          until(outE().
                count().
                is(0)).
          cap('subGraph').
          next().
          io(IoCore.graphml()).
          writeGraph(fn)
    }
   
  def static enhanceAlertsOfInterest(alertType, columns) { 
    def aoi = g.V().has('lbl', 'AlertsOfInterest').has('alertType', alertType).valueMap().toList()
    def (ip, port, table, schema) = aoi['url'][0][0].split(':')
    def client = new com.astrolabsoftware.FinkBrowser.HBaser.FinkHBaseClient(ip, port);
    def enhanced = []
    client.connect(table, schema);
    g.V().has('lbl', 'AlertsOfInterest').
          has('alertType', alertType).
          out().
          elementMap().
          each {a -> try {
                       def results = client.results2List(client.scan(a['objectId'] + '_' + a['jd'],
                                                                     null,
                                                                     columns,
                                                                     0,
                                                                     false,
                                                                     false))                     
                       def alert = g.V().has('lbl', 'alert').has('objectId', a['objectId']).has('jd', a['jd'])
                       columns.split(',').each {c -> alert.property(c.split(':')[1], results[0][c])}
                       alert.iterate()
                       enhanced += [a['objectId'] + '_' + a['jd']]
                       }
                     catch (Exception e){
                       println('Cannot enhance ' + a['objectId'] + '_' + a['jd'] + '\n' + e)
                       }
                }
    graph.tx().commit()
    return enhanced
    }
    
  def static enhanceSourcesOfInterest(sourceType, columns) { 
    // get SourceOfInterest
    g.V().has('lbl',        'SourcesOfInterest').
          has('sourceType', sourceType).
          sideEffect(values('url').
                     store('url')).
          outE().
          sideEffect(inV().values('objectId').
                           store('objectId')).
          values('instances').
          store('instances').
          cap('objectId', 'instances', 'url').
          map {
            x -> y = x.get();
            objectIds  = y['objectId' ];
            instancess = y['instances'];
            def (ip, port, table, schema) = y['url'][0].split(':')
            def client = new com.astrolabsoftware.FinkBrowser.HBaser.FinkHBaseClient(ip, port);
            client.connect(table, schema);
            // get all sources
            for (int i = 0; i < objectIds.size(); i++) {
              def objectId = objectIds[i];
              def source = g.V().has('source', 'lbl', 'source').
                                 has('objectId', objectId).
                                 fold().
                                 coalesce(unfold(), 
                                          addV('source').
                                          property('lbl',     'source'  ).
                                          property('objectId', objectId)).
                                 next();
              instances = instancess[i].replaceFirst('\\[', '').replaceAll(']', '').split(',');
              // get/create all alerts
              for (int j = 0; j < instances.size(); j++) {
                def jd = instances[j].trim();
                def key = objectId + '_' + jd;
                def results = client.results2List(client.scan(key,
                                                              null,
                                                              columns,
                                                              0,
                                                              false,
                                                              false));                                                              
                def a = g.V().has('alert', 'lbl', 'alert').
                              has('objectId', objectId).
                              has('jd',       jd).
                              fold().
                              coalesce(unfold(), 
                                       addV('alert').
                                       property('lbl',     'alert'  ).
                                       property('objectId', objectId).
                                           property('jd',       jd      ));
                columns.split(',').each {c -> a.property(c.split(':')[1], results[0][c])};
                alert = a.next();          
                g.addE('has').from(__.V(source)).to(__.V(alert)).iterate();   
                g.getGraph().tx().commit();
                }
              }
            }
    }
   
  def static graph = JanusGraphFactory.build().set("storage.backend", "hbase").set("storage.hostname", "134.158.74.54").set("storage.port", "2183").set("storage.hbase.table", "janusgraph").open()
  def static g = graph.traversal()

  }
  
// -----------------------------------------------------------------------------
  
LomikelServer.init()

globals << [graph : LomikelServer.graph]
globals << [g : LomikelServer.g]

