#!/bin/bash                                                                                                                                                                                                                                                     

# only if needed                                                                                                                                                                                                                                                
FINK_PACKAGES=\
org.apache.spark:spark-streaming-kafka-0-10-assembly_2.12:3.1.3,\
org.apache.spark:spark-sql-kafka-0-10_2.12:3.1.3,\
org.apache.spark:spark-avro_2.12:3.1.3,\
org.apache.hbase:hbase-shaded-mapreduce:2.2.7

# Other dependencies, only if needed                                                                                                                                                                                                                            
FINK_JARS=${FINK_HOME}/libs/fink-broker_2.11-1.2.jar,\
${FINK_HOME}/libs/hbase-spark-hbase2.2_spark3_scala2.11_hadoop2.7.jar,\
${FINK_HOME}/libs/hbase-spark-protocol-shaded-hbase2.2_spark3_scala2.11_hadoop2.7.jar

PYSPARK_DRIVER_PYTHON_OPTS="/opt/anaconda/bin/jupyter-notebook --debug --no-browser --port=22567" pyspark \
    --master mesos://vm-75063.lal.in2p3.fr:5050 \
    --conf spark.mesos.principal=lsst \
    --conf spark.mesos.secret=secret \
    --conf spark.mesos.role=lsst \
    --conf spark.executorEnv.HOME='/home/julius.hrivnac'\
    --driver-memory 8G --executor-memory 4G --conf spark.cores.max=32 --conf spark.executor.cores=2 \
    --packages ${FINK_PACKAGES} --jars $FINK_JARS
