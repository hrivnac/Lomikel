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

import matplotlib
import matplotlib.pyplot as plt
import numpy as np

from pyspark.sql.functions import udf
from pyspark.sql.types import StringType
from pyspark.ml.feature import StandardScaler


# classification from Fink Portal ----------------------------------------------

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


# parameters -------------------------------------------------------------------

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
          
cols = ["g00",
        "g01",
        "g02",
        "g03",
        "g04",
        "g05",
        "g06",
        "g07",
        "g08",
        "g09",
        "g10",
        "g11",
        "g12",
        "g13",
        "g14",
        "g15",
        "g16",
        "g17",
        "g18",
        "g19",
        "g20",
        "g21",
        "g22",
        "g23",
        "g24",
        "r00",
        "r01",
        "r02",
        "r03",
        "r04",
        "r05",
        "r06",
        "r07",
        "r08",
        "r09",
        "r10",
        "r11",
        "r12",
        "r13",
        "r14",
        "r15",
        "r16",
        "r17",
        "r18",
        "r19",
        "r20",
        "r21",
        "r22",
        "r23",
        "r24"]
        
lc_features = ("g00",
               "g01",
               "g02",
               "g03",
               "g04",
               "g05",
               "g06",
               "g07",
               "g08",
               "g09",
               "g10",
               "g11",
               "g12",
               "g13",
               "g14",
               "g15",
               "g16",
               "g17",
               "g18",
               "g19",
               "g20",
               "g21",
               "g22",
               "g23",
               "g24",
               "r00",
               "r01",
               "r02",
               "r03",
               "r04",
               "r05",
               "r06",
               "r07",
               "r08",
               "r09",
               "r10",
               "r11",
               "r12",
               "r13",
               "r14",
               "r15",
               "r16",
               "r17",
               "r18",
               "r19",
               "r20",
               "r21",
               "r22",
               "r23",
               "r24")

n_sample = 10000
n_pca = 25
n_clusters = 10

# new session ------------------------------------------------------------------

spark = SparkSession.builder\
                    .appName("PCA with HBase")\
                    .getOrCreate()

# read HBase into DataGram -----------------------------------------------------

df = spark.read\
          .format("org.apache.hadoop.hbase.spark")\
          .option("hbase.columns.mapping", mapping)\
          .option("hbase.table", "ztf")\
          .option("hbase.spark.use.hbasecontext", False)\
          .option("hbase.spark.pushdown.columnfilter", True)\
          .load()

df = df.filter(df.rowkey >= "ZTF24")\
       .filter(df.lc_features_g.isNotNull())\
       .filter(df.lc_features_r.isNotNull())\
       .limit(n_sample)

# convert lc_features arrays into columns --------------------------------------

split_g = split(df["lc_features_g"], ",")
df = df.withColumn("g00", split_g.getItem( 0).cast(DoubleType()))\
       .withColumn("g01", split_g.getItem( 1).cast(DoubleType()))\
       .withColumn("g02", split_g.getItem( 2).cast(DoubleType()))\
       .withColumn("g03", split_g.getItem( 3).cast(DoubleType()))\
       .withColumn("g04", split_g.getItem( 4).cast(DoubleType()))\
       .withColumn("g05", split_g.getItem( 5).cast(DoubleType()))\
       .withColumn("g06", split_g.getItem( 6).cast(DoubleType()))\
       .withColumn("g07", split_g.getItem( 7).cast(DoubleType()))\
       .withColumn("g08", split_g.getItem( 8).cast(DoubleType()))\
       .withColumn("g09", split_g.getItem( 9).cast(DoubleType()))\
       .withColumn("g10", split_g.getItem(10).cast(DoubleType()))\
       .withColumn("g11", split_g.getItem(11).cast(DoubleType()))\
       .withColumn("g12", split_g.getItem(12).cast(DoubleType()))\
       .withColumn("g13", split_g.getItem(13).cast(DoubleType()))\
       .withColumn("g14", split_g.getItem(14).cast(DoubleType()))\
       .withColumn("g15", split_g.getItem(15).cast(DoubleType()))\
       .withColumn("g16", split_g.getItem(16).cast(DoubleType()))\
       .withColumn("g17", split_g.getItem(17).cast(DoubleType()))\
       .withColumn("g18", split_g.getItem(18).cast(DoubleType()))\
       .withColumn("g19", split_g.getItem(19).cast(DoubleType()))\
       .withColumn("g20", split_g.getItem(20).cast(DoubleType()))\
       .withColumn("g21", split_g.getItem(21).cast(DoubleType()))\
       .withColumn("g22", split_g.getItem(22).cast(DoubleType()))\
       .withColumn("g23", split_g.getItem(23).cast(DoubleType()))\
       .withColumn("g24", split_g.getItem(24).cast(DoubleType()))
        
split_r = split(df["lc_features_r"], ",")
df = df.withColumn("r00", split_r.getItem( 0).cast(DoubleType()))\
       .withColumn("r01", split_r.getItem( 1).cast(DoubleType()))\
       .withColumn("r02", split_r.getItem( 2).cast(DoubleType()))\
       .withColumn("r03", split_r.getItem( 3).cast(DoubleType()))\
       .withColumn("r04", split_r.getItem( 4).cast(DoubleType()))\
       .withColumn("r05", split_r.getItem( 5).cast(DoubleType()))\
       .withColumn("r06", split_r.getItem( 6).cast(DoubleType()))\
       .withColumn("r07", split_r.getItem( 7).cast(DoubleType()))\
       .withColumn("r08", split_r.getItem( 8).cast(DoubleType()))\
       .withColumn("r09", split_r.getItem( 9).cast(DoubleType()))\
       .withColumn("r10", split_r.getItem(10).cast(DoubleType()))\
       .withColumn("r11", split_r.getItem(11).cast(DoubleType()))\
       .withColumn("r12", split_r.getItem(12).cast(DoubleType()))\
       .withColumn("r13", split_r.getItem(13).cast(DoubleType()))\
       .withColumn("r14", split_r.getItem(14).cast(DoubleType()))\
       .withColumn("r15", split_r.getItem(15).cast(DoubleType()))\
       .withColumn("r16", split_r.getItem(16).cast(DoubleType()))\
       .withColumn("r17", split_r.getItem(17).cast(DoubleType()))\
       .withColumn("r18", split_r.getItem(18).cast(DoubleType()))\
       .withColumn("r19", split_r.getItem(19).cast(DoubleType()))\
       .withColumn("r20", split_r.getItem(20).cast(DoubleType()))\
       .withColumn("r21", split_r.getItem(21).cast(DoubleType()))\
       .withColumn("r22", split_r.getItem(22).cast(DoubleType()))\
       .withColumn("r23", split_r.getItem(23).cast(DoubleType()))\
       .withColumn("r24", split_r.getItem(24).cast(DoubleType()))
        
df = df.na.fill(0, lc_features)

# normalisation ----------------------------------------------------------------

vec_assembler = VectorAssembler(inputCols=cols, outputCol="features")
df_vector = vec_assembler.transform(df)
scaler = StandardScaler(inputCol="features", outputCol="scaled_features", withMean=True, withStd=True)
scaler_model = scaler.fit(df_vector)
df_standardized = scaler_model.transform(df_vector)
#df_standardized.show(truncate=False)

# PCA --------------------------------------------------------------------------

#vecAssembler = VectorAssembler(inputCols="scaled_features", outputCol="prePcafeatures")
pca = PCA(k=n_pca, inputCol="scaled_features", outputCol="pcaFeatures")
pipeline = Pipeline(stages=[vecAssembler, pca])
model = pipeline.fit(df_standardized)
pca_model = model.stages[1]

print(pca_model.explainedVariance)
explained_variance = np.array(pca_model.explainedVariance)
cumValues = np.cumsum(explained_variance)
n_components = len(cumValues)
plt.figure(figsize=(10,8))
plt.plot(range(1, n_components + 1), cumValues, marker='o', linestyle='--')
plt.title('variance by components')
plt.xlabel('num of components')
plt.ylabel('cumulative explained variance')
plt.grid(True)
plt.savefig("/tmp/PCA_Variance.png")
# use number of components with variance about 80%

result = model.transform(df)
#result.show(truncate=False)


# Clustering -------------------------------------------------------------------  
  
## kmeans = KMeans().setK(n_clusters)\
##                  .setSeed(1)\
##                  .setFeaturesCol("pcaFeatures")\
##                  .setPredictionCol("cluster")
## kmeans_model = kmeans.fit(result)
## clustered_result = kmeans_model.transform(result)
## cr = clustered_result.select("objectId", "cluster")\
##                      .withColumn("classification", classification_udf(df.objectId))
#cr.show(truncate=False)
#cr.write\
#  .mode("overwrite")\
#  .format("csv")\
#  .save("/tmp/cr")

# statistics -------------------------------------------------------------------

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

# end --------------------------------------------------------------------------

spark.stop()


