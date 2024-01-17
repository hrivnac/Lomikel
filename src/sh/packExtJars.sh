source ../ant/build-global.properties
source ../ant/build-local.properties

CLASSPATH=`${hadoop_dir}/bin/hadoop classpath --glob`:`${hbase_dir}/bin/hbase classpath --glob`
mkdir -p HBaseJars
for J in `echo ${CLASSPATH} | sed 's/:/ /g'`; do
  if [[ `basename ${J} .jar` != `basename ${J}` ]]; then
    cp ${J} HBaseJars
    fi
  done

mkdir -p JanusJars
for J in `ls ${janusgraph_dir}/lib/*jar`; do
  cp ${J} JanusJars
  done
  
for X in HBaseJars JanusJars; do
  rm -f ${X}.tar*
  tar cvf ../dist/${X}.tar ${X}
  gzip ../dist/${X}.tar
  rm -rf ${X}
done
