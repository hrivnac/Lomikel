from pyspark.sql import SparkSession
from pyspark.sql import functions as F
from pyspark.sql.functions import col
from pyspark.sql.functions import mean
from pyspark.sql.functions import stddev
from pyspark.sql.functions import udf
from pyspark.sql.functions import lit
from pyspark.sql.functions import split
from pyspark.sql.types import StringType
from pyspark.sql.types import DoubleType
from pyspark.ml import Pipeline
from pyspark.ml.feature import PCA
from pyspark.ml.feature import VectorAssembler
from pyspark.ml.feature import StandardScaler
from pyspark.ml.linalg import Vectors
from pyspark.ml.clustering import KMeans
from pyspark.ml.clustering import BisectingKMeans
from pyspark.ml.evaluation import ClusteringEvaluator

import numpy as np
from numpy import array

import matplotlib
import matplotlib.pyplot as plt

import seaborn as sns

from mpl_toolkits.mplot3d import Axes3D

import math
import requests
import random
import json
import csv

# Classification from Fink Portal ----------------------------------------------

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

# Parameters -------------------------------------------------------------------

pca_sample = "/tmp/PCA-sample.csv"

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
          
extra_cols = ["magpsf", "sigmapsf", "magnr", "sigmagnr", "magzpsci"]

pca_sample = "/tmp/PCA-sample.csv"
rowkey_start = "ZTF24"
n_sample = 1000
n_pca = 10
n_clusters = 10
read_sample = True
add_extra_cols = False
silhouette = False
classify = True
cluster_features = "pca_features"

# Read PCA sample --------------------------------------------------------------

rks = []
if read_sample:
  with open(pca_sample, newline='') as csvfile:
    csvreader = csv.reader(csvfile, delimiter=',')
    for row in csvreader:
      objectId = row[1]
      jdList = row[2].split(';')
      for jd in jdList:
        rk = objectId + "_" + jd
        classifications[rk] = row[0]
        rks.append(rk)
  random.shuffle(rks)
  rks = rks[0:n_sample]

# New session ------------------------------------------------------------------

spark = SparkSession.builder\
                    .appName("PCA CLustering with HBase")\
                    .getOrCreate()

# Read HBase into DataGram -----------------------------------------------------

df = spark.read\
          .format("org.apache.hadoop.hbase.spark")\
          .option("hbase.columns.mapping", mapping)\
          .option("hbase.table", "ztf")\
          .option("hbase.spark.use.hbasecontext", False)\
          .option("hbase.spark.pushdown.columnfilter", True)\
          .load()

if read_sample:
  df = df.filter(df.rowkey.isin(rks))
else:  
  df = df.filter(df.rowkey >= rowkey_start)

df = df.filter(df.lc_features_g.isNotNull())\
       .filter(df.lc_features_r.isNotNull())\
       .limit(n_sample)

# Convert lc_features arrays into columns --------------------------------------
       
lc_features = tuple(f"g{i:02d}" for i in range(25)) \
            + tuple(f"r{i:02d}" for i in range(25))

cols = list(lc_features)
if add_extra_cols:
    cols += extra_cols

df = df.selectExpr("*", *(f"CAST(split(lc_features_g, ',')[{i}] AS DOUBLE) AS g{i:02d}" for i in range(26)))\
       .selectExpr("*", *(f"CAST(split(lc_features_r, ',')[{i}] AS DOUBLE) AS r{i:02d}" for i in range(26)))   
      
df = df.drop("lc_features_g")\
       .drop("lc_features_r")

#df = df.na.fill(0, lc_features)
mean_values = df.select([mean(col(c))\
                .alias(c) for c in lc_features])\
                .collect()[0]\
                .asDict()
mean_values = {k: (v if (v is not None and not math.isnan(v)) else 0) for k, v in mean_values.items()}
df = df.na.fill(mean_values)
                   
# Classification ---------------------------------------------------------------  
   
if classify:
  df = df.withColumn("classification", classification_udf(df.rowkey))
  df = df.filter((df.classification != "failed") & (df.classification != "Unknown"))                     

# report
print("Initial shape: ", df.count(), len(df.columns))

# Standardisation --------------------------------------------------------------

vec_assembler = VectorAssembler(inputCols=cols,
                                outputCol="features",
                                handleInvalid="skip")
df_vector = vec_assembler.transform(df)
scaler = StandardScaler(inputCol="features",
                        outputCol="scaled_features",
                        withMean=True,
                        withStd=True)
scaler_model = scaler.fit(df_vector)
df_standardized = scaler_model.transform(df_vector)

# export
scaler_params = {
  "mean": scaler_model.mean.toArray().tolist(),
  "std": scaler_model.std.toArray().tolist()
  }
with open("/tmp/scaler_params.json", "w") as f:
  json.dump(scaler_params, f)

# PCA --------------------------------------------------------------------------

pca = PCA(k=n_pca,
          inputCol="scaled_features",
          outputCol="pca_features")
pca_model = pca.fit(df_standardized)
df_pca = pca_model.transform(df_standardized)

# report
#print(pca_model.explainedVariance)

# export
pca_params = {
  "components": [row.tolist() for row in pca_model.pc.toArray()],
  "explained_variance": pca_model.explainedVariance.toArray().tolist()
  }
with open("/tmp/pca_params.json", "w") as f:
    json.dump(pca_params, f)
    
# plot
explained_variance = np.array(pca_model.explainedVariance)
cumValues = np.cumsum(explained_variance)
n_components = len(cumValues)
plt.figure(figsize=(10, 8))
plt.plot(range(1, n_components + 1),
         cumValues,
         marker="o",
         linestyle="--")
plt.title("variance by components")
plt.xlabel("num of components")
plt.ylabel("Cumulative Explained Variance")
plt.grid(True)
plt.savefig("/tmp/PCA_Variance.png")

# use n_components for variance about 80%

# Clustering -------------------------------------------------------------------  
    
evaluator = ClusteringEvaluator().setPredictionCol("prediction")\
                                 .setFeaturesCol(cluster_features)\
                                 .setMetricName("silhouette",)\
                                 .setDistanceMeasure("squaredEuclidean")

if silhouette:
  silhouette_score = [] 
    
  for i in range(5, 25):
    try:
      ## kmeans = KMeans().setK(i)\
      ##                  .setFeaturesCol(cluster_features) 
      kmeans = BisectingKMeans().setK(i)\
                                .setFeaturesCol(cluster_features)
      model = kmeans.fit(df_pca)
      predictions = model.transform(df_pca)
      score = evaluator.evaluate(predictions) 
      silhouette_score.append(score)
    except:
      print("Failed for i = ", i)
      silhouette_score.append(0)
  # plot
  plt.figure(figsize=(10, 8))
  plt.plot(range(5, 25), silhouette_score) 
  plt.xlabel("number of clusters") 
  plt.ylabel("within set sum of squared errors") 
  plt.title("Elbow Method for Optimal K") 
  plt.grid()
  plt.savefig("/tmp/Silhouette_Score.png")  
  # use n_clusters at maximum
    
## kmeans = KMeans().setK(n_clusters)\
##                  .setSeed(1)\
##                  .setFeaturesCol(cluster_features)\
##                  .setPredictionCol("cluster")
kmeans = KMeans().setK(n_clusters)\
                 .setSeed(1)\
                 .setFeaturesCol(cluster_features)\
                 .setPredictionCol("cluster")
kmeans_model = kmeans.fit(df_pca)
clustered_result = kmeans_model.transform(df_pca)
cr = clustered_result.select("objectId", "cluster", "classification")

# export
cluster_centers = [center.tolist() for center in kmeans_model.clusterCenters()]

with open("/tmp/cluster_centers.json", "w") as f:
  json.dump(cluster_centers, f)

# plot                     
pdf = cr.select("cluster", "classification").toPandas()
pdf["cluster"] = pdf["cluster"].astype(str)
grouped = pdf.groupby(["classification", "cluster"])\
             .size()\
             .reset_index(name="count")
plt.figure(figsize=(12, 6))
sns.scatterplot(data=grouped,
                x="cluster",
                y="classification",
                size="count",
                hue="count",
                palette="viridis",
                sizes=(50, 500),
                edgecolor="black",
                alpha=0.75)
plt.xlabel("Classification")
plt.ylabel("Cluster")
plt.title("Cluster vs Classification Scatter Plot (Bubble Size = Count)")
plt.xticks(rotation=45)
plt.grid(True)
plt.legend(title="Count")
plt.savefig("/tmp/Classification_Clusters.png")

centers = kmeans_model.clusterCenters() 
print("Cluster Centers:") 
for center in centers: 
  print(center)

# show
#cr.show(truncate=False)

# export
#cr.write\
#  .mode("overwrite")\
#  .format("csv")\
#  .save("/tmp/cr")

# Statistics -------------------------------------------------------------------

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

# End --------------------------------------------------------------------------

spark.stop()


