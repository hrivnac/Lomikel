from pyspark.sql import SparkSession
from pyspark.sql import functions as F
from pyspark.sql.functions import col
from pyspark.sql.functions import mean
from pyspark.sql.functions import stddev
from pyspark.sql.functions import udf
from pyspark.sql.functions import lit
from pyspark.sql.functions import split
from pyspark.sql.types import DoubleType
from pyspark.ml import Pipeline
from pyspark.ml.feature import PCA
from pyspark.ml.feature import VectorAssembler
from pyspark.ml.linalg import Vectors
from pyspark.ml.clustering import KMeans
from pyspark.ml.evaluation import ClusteringEvaluator
from numpy import array
from math import sqrt
import requests
import json

from pyspark.sql.functions import udf
from pyspark.sql.types import StringType

classifications = {}

def classification(objectId):
  if objectId not in classifications:
    try:
      r = requests.post("https://api.fink-portal.org/api/v1/objects",
                        json={"objectId": objectId, "output-format": "json"})  
      s = json.loads(r.text)  
      t = s[0]["v:classification"]
      classifications[objectId] = t
    except:
      classifications[objectId] = "failed"
  return classifications[objectId]
 
classification_udf = udf(lambda x: classification(x), StringType())

spark = SparkSession.builder.appName("PCA with HBase").getOrCreate()

print("*** DF ***")
mapping = "rowkey STRING :key, " + \
          "objectId STRING i:objectId, " + \
          "lc_features_g STRING d:lc_features_g, " \
          "lc_features_r STRING d:lc_features_r, " \
          "jd FLOAT i:jd, " + \
          "xpos FLOAT i:xpos, " + \
          "ypos FLOAT i:ypos, " + \
          "magpsf FLOAT i:magpsf, " + \
          "sigmapsf FLOAT i:sigmapsf, " + \
          "magnr FLOAT i:magnr, " + \
          "sigmagnr FLOAT i:sigmagnr, " + \
          "magzpsci FLOAT i:magzpsci"
cols = ["magpsf", 
        "sigmapsf",
        "magnr",
        "sigmagnr",
        "magzpsci"]
df = spark.read.format("org.apache.hadoop.hbase.spark").option("hbase.columns.mapping", mapping).option("hbase.table", "ztf").option("hbase.spark.use.hbasecontext", False).option("hbase.spark.pushdown.columnfilter", True).load().filter(~F.col("rowkey").startswith("schema_")).limit(1000)

df = df.filter(df.lc_features_g.isNotNull()).filter(df.lc_features_r.isNotNull())

#df = df.select("lc_features_g.*").toDF("g00","g01","g02","g03","g04","g05","g06","g07","g08","g09","g10","g11","g12","g13","g14","g15","g16","g17","g18","g19","g20","g21","g22","g23","g24")

#df = df.select(split(col("lc_features_g"), ","))

split_col = split(df["lc_features_g"], ",")
df = df.withColumn('g00', split_col.getItem(0)).\
        withColumn('g01', split_col.getItem(1))     
df.show(truncate=False)

##print("*** VectorAssembler ***")
##vecAssembler = VectorAssembler(inputCols=cols, outputCol="features")
##  
##print ("*** PCA ***")
##pca = PCA(k=5, inputCol="features", outputCol="pcaFeatures")
##pipeline = Pipeline(stages=[vecAssembler, pca])
##model = pipeline.fit(df)
##result = model.transform(df)
###result.show(truncate=False)
##  
##print("*** Clustering ***")
##kmeans = KMeans().setK(5).setSeed(1).setFeaturesCol("pcaFeatures").setPredictionCol("cluster")
##kmeans_model = kmeans.fit(result)
##clustered_result = kmeans_model.transform(result)
##cr = clustered_result.select("objectId", "cluster").withColumn("classification", classification_udf(df.objectId))
###cr.show(n=1000, truncate=False)
##cr.write.format("csv").save("/tmp/cr")

print("*** Centers ***")
#centers = kmeans_model.clusterCenters()
#for idx, center in enumerate(centers):
#  print(f"Cluster {idx}: {center}")

print("*** Counts ***")
#clustered_result.groupBy("cluster").count().show()

print("*** Stats ***")
#get_element = udf(lambda vector, idx: float(vector[idx]), DoubleType())
#clustered_result = clustered_result.withColumn("pca_1", get_element("pcaFeatures", lit(0))) \
#                                   .withColumn("pca_2", get_element("pcaFeatures", lit(1)))
#cluster_stats = clustered_result.groupBy("cluster").agg(
#    mean("pca_1").alias("mean_pca_1"),
#    stddev("pca_1").alias("stddev_pca_1"),
#    mean("pca_2").alias("mean_pca_2"),
#    stddev("pca_2").alias("stddev_pca_2")
#)
#cluster_stats.show(truncate=False)

#evaluator = ClusteringEvaluator(featuresCol="pcaFeatures", predictionCol="cluster", metricName="silhouette")
#silhouette = evaluator.evaluate(clustered_result)
#print(silhouette)

spark.stop()


