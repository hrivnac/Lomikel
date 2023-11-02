import groovy.sql.Sql

sql = Sql.newInstance("jdbc:phoenix:ithdp2101.cern.ch:2181", 
                      "org.apache.phoenix.jdbc.PhoenixDriver")

//sql = Sql.newInstance("jdbc:phoenix:thin:url=http://ithdp2101.cern.ch:2181;serialization=PROTOBUF", 
//                      "org.apache.phoenix.jdbc.PhoenixDriver")

sql.eachRow("select * from AEI.DATASETS_0  limit 1") {println it}
sql.eachRow("select * from AEI.CANONICAL_0 limit 1") {println it}
sql.eachRow("select * from AEI.EVENTS_0    limit 1") {println it}
sql.eachRow("select * from AEI.TRIGMENU_0  limit 1") {println it}
sql.eachRow("select * from AEI.DSTYPES_0          ") {println it}

