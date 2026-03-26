source build-global.properties
source build-local.properties

export home=`pwd`/..

if [[ ! "x" = "x${ant_home}" ]]; then
  export ANT_HOME="${ant_home}"
  export PATH="${ANT_HOME}/bin:${PATH}"
  fi
if [[ ! "x" = "x${java_home}" ]]; then
  export JAVA_HOME="${java_home}"
  export PATH="${JAVA_HOME}/bin:${PATH}"
  fi
if [[ ! "x" = "x${graphviz}" ]]; then
  export PATH="${graphviz}/bin:${PATH}"
  fi
HADOOP_CLASSPATH=`${hadoop_dir}/bin/hadoop classpath --glob`
HBASE_CLASSPATH=`${hbase_dir}/bin/hbase classpath --glob`
export JANUSGRAPH_CLASSPATH=""
for JAR in ${janusgraph_dir}/lib/*.jar; do
  JANUSGRAPH_CLASSPATH=${JANUSGRAPH_CLASSPATH}:${JAR} 
  done
export GROOVY_CLASSPATH=""
for JAR in ${janusgraph_dir}/lib/*.jar; do
  JANUSGRAPH_CLASSPATH=${JANUSGRAPH_CLASSPATH}:${JAR} 
  done
export LOMIKEL_CLASSPATH="../lib/Lomikel-${version}.exe.jar:${jython_jar}:${JANUSGRAPH_CLASSPATH}"
export GREMLIN_CLASSPATH="${groovy_sql_jar}"
    
export janusgraph_dir
export hadoop_dir
export hbase_dir
export gremlin_dir
export zookeeper
export hbase_table
 
alias gremlin_console_Local='CLASSPATH="${GREMLIN_CLASSPATH}" ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_console.gremlin ${janusgraph_dir}/conf/gremlin-server/Local.properties ${home}"'
alias gremlin_console_IJCLab='CLASSPATH="${GREMLIN_CLASSPATH}" ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_console.gremlin ${janusgraph_dir}/conf/gremlin-server/Local-IJCLab.properties ${home}"'
alias gremlin_console_CC='CLASSPATH="${GREMLIN_CLASSPATH}" ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_console.gremlin ${janusgraph_dir}/conf/gremlin-server/Local-CC.properties ${home}"'
alias gremlin_Local='CLASSPATH=""  ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_Local.gremlin"'
alias gremlin_IJCLab='CLASSPATH="" ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_IJCLab.gremlin"'
alias gremlin_CC='CLASSPATH="" ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_CC.gremlin"'
alias lomikel='CLASSPATH="${LOMIKEL_CLASSPATH}" java com.Lomikel.Apps.LUC'
alias lomikel_pure='java -jar ../lib/Lomikel-${version}.exe.jar'
alias lomikel_hbase='java -jar ../lib/Lomikel-HBase-${version}.exe.jar'
alias lomikel_hadoop='java --add-opens java.base/java.lang=ALL-UNNAMED -jar ../lib/Lomikel-Hadoop-${version}.exe.jar'
alias lomikel_janus='java -jar ../lib/Lomikel-Janus-${version}.exe.jar'
alias lomikel_all='java --add-opens=java.base/java.lang=ALL-UNNAMED -jar ../lib/Lomikel-All-${version}.exe.jar'
alias lomikel_dl4j='java --enable-preview -jar ../lib/Lomikel-dl4j-${version}.exe.jar'

echo "commands: gremlin_console_Local, gremlin_console_IJCLab, gremlin_console_CC, gremlin_Local, gremlin_IJCLab, gremlin_CC, lomikel, lomikel_pure, lomikel_hbase, lomikel_hadoop, lomikel_janus, lomikel_all, lomikel_dl4j"
 