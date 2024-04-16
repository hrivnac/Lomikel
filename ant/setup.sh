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
export LOMIKEL_CLASSPATH="../lib/Lomikel.exe.jar:${JANUSGRAPH_CLASSPATH}"
export GREMLIN_CLASSPATH="${groovy_sql_jar}":"${bsh_jar}"
    
export janusgraph_dir
export hadoop_dir
export hbase_dir
export gremlin_dir
export zookeeper
export hbase_table
 
alias gremlin_console_Local='CLASSPATH="${GREMLIN_CLASSPATH}" ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_console.gremlin ${janusgraph_dir}/conf/gremlin-server/Local.properties ${home}"'
alias gremlin_console_IJCLab='CLASSPATH="${GREMLIN_CLASSPATH}" ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_console.gremlin ${janusgraph_dir}/conf/gremlin-server/Local-IJCLab.properties ${home}"'
alias gremlin_Local='CLASSPATH=""  ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_Local.gremlin"'
alias gremlin_IJCLab='CLASSPATH="" ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_IJCLab.gremlin"'
alias lomikel='CLASSPATH="${LOMIKEL_CLASSPATH}" java com.Lomikel.Apps.LUC'

echo "commands: gremlin_console_Local, gremlin_console_IJCLab, gremlin_Local, gremlin_IJCLab, lomikel"
