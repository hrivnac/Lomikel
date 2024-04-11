CP0=`${hadoop_dir}/bin/hadoop classpath --glob`
CP=""
for P in `echo ${CP0} | sed 's/:/ /g'`; do
  CP="${CP}:${P}"
  done
echo ${CP}
