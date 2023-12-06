CP0=`/opt/hadoop/bin/hadoop classpath`
CP=""
for P in `echo ${CP0} | sed 's/:/ /g'`; do
  CP="${CP}:${P}"
  done
echo ${CP}
