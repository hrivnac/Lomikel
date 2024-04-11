CP0=`${hbase_dir}/bin/hbase classpath --glob`
CP=""
for P in `echo ${CP0} | sed 's/:/ /g'`; do
  CP="${CP}:${P}"
  done
echo ${CP}
