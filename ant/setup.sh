source build-global.properties
source build-local.properties

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
  
export janusgraph_dir
export hbase_dir
export gremlin_dir
export zookeeper
export hbase_table

export home=`pwd`/..

alias gremlin_Local='CLASSPATH="${phoenix_jar}"   ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_Local.gremlin"'
alias gremlin_IJCLab='CLASSPATH="${phoenix_jar}"  ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_IJCLab.gremlin"'
alias gremlin_CERN='CLASSPATH="${phoenix_jar}"    ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_CERN.gremlin"'
alias gremlin_console='CLASSPATH="${phoenix_jar}" ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_console.gremlin ${janusgraph_dir}/conf/gremlin-server/Local.properties ${home}"'

echo "commands: gremlin_console, gremlin_Local, gremlin_IJCLab"

