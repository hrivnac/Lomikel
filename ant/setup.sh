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
if [[ ! "x" = "x${hbase_classpath}" ]]; then
  export CLASSPATH="${hbase_classpath}"
  fi
if [[ `uname -a  |awk '{print $2}' | awk -F. '{print $2}'` = "cern" ]]; then
  export GREMLIN_CLASSPATH="${groovy_sql_jar}:${janusgraph_dir}/*.jar:${hadoop_phoenix_conf}:${hbase_phoenix_conf}:${phoenix_jar}"
else
  export GREMLIN_CLASSPATH=""
  fi
  
export janusgraph_dir
export hbase_dir
export gremlin_dir
export zookeeper
export hbase_table

if [[ `uname -a  |awk '{print $2}' | awk -F. '{print $2}'` = "cern" ]]; then
  HBASE_CONF_DIR='${phoenix_conf_dir}'
else
  HBASE_CONF_DIR=''
  fi
alias gremlin_Local='HBASE_CONF_DIR=${HBASE_CONF_DIR} CLASSPATH="${GREMLIN_CLASSPATH}" ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_Local.gremlin"'
alias gremlin_IJCLab='${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_IJCLab.gremlin"'
alias gremlin_CERN='${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_CERN.gremlin"'
alias gremlin_console='${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_console.gremlin ${janusgraph_dir}/conf/gremlin-server/Local.properties ${home}"'

echo "commands: gremlin_console, gremlin_Local, gremlin_IJCLab, gremlin_CERN"
