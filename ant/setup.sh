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
export GREMLIN_CLASSPATH=`pwd`/"${groovy_sql_jar}":`pwd`/"${bsh_jar}" 
    
export janusgraph_dir
export hbase_dir
export gremlin_dir
export zookeeper
export hbase_table
 
alias gremlin_console_Local='CLASSPATH="${GREMLIN_CLASSPATH}" ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_console.gremlin ${janusgraph_dir}/conf/gremlin-server/Local.properties ${home}"'
alias gremlin_console_IJCLab='CLASSPATH="${GREMLIN_CLASSPATH}" ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_console.gremlin ${janusgraph_dir}/conf/gremlin-server/Local-IJCLab.properties ${home}"'
alias gremlin_Local='CLASSPATH=""  ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_Local.gremlin"'
alias gremlin_IJCLab='CLASSPATH="" ${janusgraph_dir}/bin/gremlin.sh -i "../src/gremlin/start_IJCLab.gremlin"'
alias lomikel='CLASSPATH="../lib/Lomikel-${version}.jar:${phoenix_jar}" java com.Lomikel.Apps.LUC'

echo "commands: gremlin_console_Local, gremlin_console_IJCLab, gremlin_console_CERN, gremlin_Local, gremlin_IJCLab, gremlin_CERN, lomikel"
