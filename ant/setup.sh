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
export zookeeper
export hbase_table
export hbase_dir

export home=`pwd`/..

alias gremlin_Local='CLASSPATH="" ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_Local.gremlin"'
alias gremlin_IJCLab='CLASSPATH="" ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_IJCLab.gremlin"'
alias gremlin_console='CLASSPATH="" ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_console.gremlin ${zookeeper} ${hbase_table} ${home}" -i "../src/gremlin/describe.gremlin"'

echo "commands: gremlin_console, gremlin_Local, gremlin_IJCLab"

