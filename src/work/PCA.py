from pyspark.sql import SparkSession
from pyspark.ml.feature import PCA, VectorAssembler
from pyspark.ml.linalg import Vectors
from pyspark.sql.functions import col
from pyspark.ml import Pipeline
from pyspark.sql import functions as F

spark = SparkSession.builder.appName("PCA with HBase").getOrCreate()

df = spark.read.format("org.apache.hadoop.hbase.spark").option("hbase.columns.mapping", "name STRING :key, jd DOUBLE i:jd, xpos FLOAT i:xpos, ypos FLOAT i:ypos").option("hbase.table", "ztf").option("hbase.spark.use.hbasecontext", False).option("hbase.spar\
k.pushdown.columnfilter", True).load().filter(~F.col("name").startswith("schema_")).limit(1000)

#df.printSchema()                                                                                                                                                                                                                                               

#df.show(False)                                                                                                                                                                                                                                                 


#df.createOrReplaceTempView("v")                                                                                                                                                                                                                                
#results = spark.sql("SELECT * FROM v LIMIT 5")                                                                                                                                                                                                                 
#results.show()                                                                                                                                                                                                                                                 

df.show()

vecAssembler = VectorAssembler(inputCols=["xpos","ypos"], outputCol="features")

pca = PCA(k=2, inputCol="features", outputCol="pcaFeatures")
pipeline = Pipeline(stages=[vecAssembler, pca])
model = pipeline.fit(df)
result = model.transform(df).select("pcaFeatures")
#result.show(truncate=False)                                                                                                                                                                                                                                    

print(pca.explainParams())
print(model.explainParams())

spark.stop()
