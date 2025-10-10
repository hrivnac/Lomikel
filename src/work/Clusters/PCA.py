from pyspark.sql import SparkSession
from pyspark.sql import functions as F
from pyspark.sql.functions import size
from pyspark.sql.functions import col
from pyspark.sql.functions import mean
from pyspark.sql.functions import stddev
from pyspark.sql.functions import udf
from pyspark.sql.functions import lit
from pyspark.sql.functions import split
from pyspark.sql.functions import isnan
from pyspark.sql.functions import pandas_udf
from pyspark.sql.types import StringType
from pyspark.sql.types import DoubleType
from pyspark.sql.types import ArrayType
from pyspark.ml import Pipeline
from pyspark.ml.feature import PCA
from pyspark.ml.feature import VectorAssembler
from pyspark.ml.feature import StandardScaler
from pyspark.ml.linalg import Vectors
from pyspark.ml.clustering import KMeans
from pyspark.ml.clustering import BisectingKMeans
from pyspark.ml.evaluation import ClusteringEvaluator

from fink_filters.ztf.classification import extract_fink_classification

from functools import reduce

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
import os
import glob

# Parameters -------------------------------------------------------------------

#source = "ZTF"
source = "LSST"
skipNaN = False
replaceNaNbyMean = False
replaceNaNbyZero = True
n_sample = 0
n_pca = 13
n_clusters_start = 5
n_clusters = 30
silhouette = False
cluster_features = "pca_features"
known = False
tag = "-zero"
clean = True

if (source == "ZTF"):
  dataFn = "/user/julien.peloton/archive/science/year=2025"
elif (source == "LSST"):
  dataFn = "/user/julien.peloton/rubin_alert_commissioning/raw/20250906"
else:
  log.fatal("No source")
  sys.exit()
  
# NestedDF ---------------------------------------------------------------------

class NestedDF:
  """A class for flattening nested dataframes in PySpark."""

  def __init__(self, nested_df):
    """
    Args:
      nested_df (pyspark.sql.dataframe.DataFrame): Nested dataframe.
    """
    self.nested_df = nested_df
    self.flattened_struct_df = self.flatten_struct_df()
    self.flattened_df = self.flatten_array_df()
  
  def flatten_array_df(self):
    """Flatten a nested array dataframe into a single level dataframe.
    Returns:
      pyspark.sql.dataframe.DataFrame: Flattened dataframe.  
    """
    cols = self.flattened_struct_df.columns
    for col_name in cols:
      if isinstance(self.flattened_struct_df.schema[col_name].dataType, ArrayType):
        array_len = self.flattened_struct_df.select(size(col(col_name)).alias("array_len")).collect()[0]["array_len"]
        for i in range(array_len):
          self.flattened_struct_df = self.flattened_struct_df.withColumn(col_name + "_" + str(i), self.flattened_struct_df[col_name].getItem(i))
        self.flattened_struct_df = self.flattened_struct_df.drop(col_name)
    return self.flattened_struct_df
    
  def flatten_struct_df(self):
    """Flatten a nested dataframe into a single level dataframe.
    Returns:
        pyspark.sql.dataframe.DataFrame: Flattened dataframe.
    """
    stack=[((), self.nested_df)]
    columns=[]
    while len(stack)>0:
      parents, df=stack.pop()
      for col_name, col_type in df.dtypes:
        if col_type.startswith('struct'):
            stack.append((parents+(col_name,), df.select(col_name+".*")))
        else:
            columns.append(col(".".join(parents+(col_name,))).alias("_".join(parents+(col_name,))))
    return self.nested_df.select(columns)

# Clean ------------------------------------------------------------------------

if clean:
  for f in glob.glob("/tmp/*.png"):
    os.remove(f)
  for f in glob.glob("/tmp/*.json"):
    os.remove(f)

# New session ------------------------------------------------------------------

spark = SparkSession.builder\
                    .appName("PCA CLustering")\
                    .getOrCreate()
                    
log4jLogger = spark._jvm.org.apache.log4j
log = log4jLogger.LogManager.getLogger("PCA")
log.info("Starting...")

# Reading Parquet file into DataFrame ------------------------------------------

df = spark.read\
          .format("parquet")\
          .load(dataFn)
   
if (source == "LSST"):
  df = NestedDF(df).flattened_df  
  
#df.show(n = 2)
#df.describe().show()
#df.printSchema()

if (source == "ZTF"):
  df = df.filter(df.lc_features_g.isNotNull())\
         .filter(df.lc_features_r.isNotNull())
#elif (source == "LSST"):
 
if n_sample > 0:
  df = df.limit(n_sample)        

# Classification ---------------------------------------------------------------

cols = None

if (source == "ZTF"):

  args = ["cdsxmatch",
          "roid",
          "mulens",
          "snn_snia_vs_nonia",
          "snn_sn_vs_all",
          "rf_snia_vs_nonia",
          "candidate.ndethist",
          "candidate.drb",
          "candidate.classtar",
          "candidate.jd",
          "candidate.jdstarthist",
          "rf_kn_vs_nonkn",
          "tracklet"]
  
  df = df.withColumn("class", extract_fink_classification(*args))
         
  if known:
    df = df.filter(df.cdsxmatch != "Unknown")
  
# Converting lc_features arrays into columns -----------------------------------
      
if (source == "ZTF"):
  feature_names = ["mean",
                   "weighted_mean",
                   "standard_deviation",
                   "median",
                   "amplitude", 
                   "beyond_1_std",
                   "cusum",
                   "inter_percentile_range_10",
                   "kurtosis", 
                   "linear_trend",
                   "linear_trend_sigma",
                   "linear_trend_noise", 
                   "linear_fit_slope",
                   "linear_fit_slope_sigma",
                   "linear_fit_reduced_chi2", 
                   "magnitude_percentage_ratio_40_5",
                   "magnitude_percentage_ratio_20_10", 
                   "maximum_slope",
                   "median_absolute_deviation",
                   "median_buffer_range_percentage_10", 
                   "percent_amplitude",
                   "mean_variance",
                   "anderson_darling_normal", 
                   "chi2",
                   "skew",
                   "stetson_K"]  
  columns = [col("class")]\
          + [col("objectId")]\
          + [col("candidate.jd").alias("jd")]\
          + [col(f"lc_features_g.{feat}").alias(f"g_{feat}") for feat in feature_names]\
          + [col(f"lc_features_r.{feat}").alias(f"r_{feat}") for feat in feature_names]  
  df = df.select(*columns)\
         .drop("lc_features_g", "lc_features_r")           
  cols = [c for c in df.columns if (c != "class" and c != "objectId" and c != "jd")]
elif (source == "LSST"):   
  cols = ["dipoleLength",
          "dipoleAngle",
          "dipoleChi2",
          "scienceFlux",
          "ixx",
          "iyy",
          "ixy",
          "ixxPSF",
          "iyyPSF",
          "ixyPSF"]
  columns = [col(c) for c in cols]

if skipNaN: # cuts number of alerts to 1/4
  df = df.na.drop(subset = cols)
  df = df.filter(reduce(lambda x, y: x & ~isnan(col(y)), cols, lit(True)))

if replaceNaNbyMean:
  mean_values = df.select([mean(col(c)).alias(c) for c in df.columns if c != "class"])\
                  .collect()[0]\
                  .asDict()
  mean_values = {k: (v if v is not None and not math.isnan(v) else 0) for k, v in mean_values.items()}  
  df = df.na.fill(mean_values)
  
if replaceNaNbyZero:
  df = df.na.fill(0)  
    
log.info("Initial shape: " + str(df.count()) + " * " + str(len(df.columns)))
log.info(cols)

# Standardisation --------------------------------------------------------------

vec_assembler = VectorAssembler(inputCols     = cols,
                                outputCol     = "features",
                                handleInvalid = "skip")
df_vector = vec_assembler.transform(df)
scaler = StandardScaler(inputCol  = "features",
                        outputCol = "scaled_features",
                        withMean  = True,
                        withStd   = True)
scaler_model = scaler.fit(df_vector)
df_standardized = scaler_model.transform(df_vector)

# export
scaler_params = {
  "mean": scaler_model.mean\
                      .toArray()\
                      .tolist(),
  "std": scaler_model.std\
                     .toArray()\
                     .tolist()
  }
with open("/tmp/scaler_params" + tag + ".json", "w") as f:
  json.dump(scaler_params, f)

df_standardized.show()
sys.exit()


# PCA --------------------------------------------------------------------------

pca = PCA(k         = n_pca,
          inputCol  = "scaled_features",
          outputCol = "pca_features")
pca_model = pca.fit(df_standardized)
df_pca = pca_model.transform(df_standardized)

# report
log.info("Variance: " + str(pca_model.explainedVariance))

# export
pca_params = {
  "components": [row.tolist() for row in pca_model.pc\
                                                  .toArray()],
  "explained_variance": pca_model.explainedVariance\
                                 .toArray()\
                                 .tolist()
  }
with open("/tmp/pca_params" + tag + ".json", "w") as f:
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
plt.savefig("/tmp/PCA_Variance-" + str(n_pca) + tag + ".png")

# use n_pca for variance about 80%

# Clustering -------------------------------------------------------------------  
    
if silhouette:
  evaluator = ClusteringEvaluator().setPredictionCol("prediction")\
                                   .setFeaturesCol(cluster_features)\
                                   .setMetricName("silhouette",)\
                                   .setDistanceMeasure("squaredEuclidean")
  silhouette_score = []   
  for i in range(n_clusters_start, n_clusters):
    try:
      kmeans = KMeans().setK(i)\
                       .setFeaturesCol(cluster_features) 
      ## kmeans = BisectingKMeans().setK(i)\
      ##                           .setFeaturesCol(cluster_features)
      model = kmeans.fit(df_pca)
      predictions = model.transform(df_pca)
      score = evaluator.evaluate(predictions) 
      silhouette_score.append(score)
    except:
      log.error("Failed for i = " + str(i))
      silhouette_score.append(0)
  # plot
  plt.figure(figsize=(10, 8))
  plt.plot(range(n_clusters_start, n_clusters), silhouette_score) 
  plt.xlabel("number of clusters") 
  plt.ylabel("within set sum of squared errors") 
  plt.title("Elbow Method for Optimal K") 
  plt.grid()
  plt.savefig("/tmp/Silhouette_Score-" + str(n_pca) + "-" + str(n_clusters) + tag + ".png")  
  # use n_clusters at maximum

kmeans = KMeans().setK(n_clusters)\
                 .setSeed(1)\
                 .setFeaturesCol(cluster_features)\
                 .setPredictionCol("cluster")
kmeans_model = kmeans.fit(df_pca)
clustered_result = kmeans_model.transform(df_pca)
cr = clustered_result.select("objectId", "cluster", "class")

# export
cluster_centers = [center.tolist() for center in kmeans_model.clusterCenters()]
with open("/tmp/cluster_centers" + tag + ".json", "w") as f:
  json.dump(cluster_centers, f)
  
# export
cr.write\
  .mode("overwrite")\
  .format("csv")\
  .save("/tmp/Clusters")

# plot                     
pdf = cr.select("cluster", "class").toPandas()
pdf["cluster"] = pdf["cluster"].astype(str)
grouped = pdf.groupby(["class", "cluster"])\
             .size()\
             .reset_index(name="count")
plt.figure(figsize=(12, 6))
sns.scatterplot(data=grouped,
                x         = "cluster",
                y         = "class",
                size      = "count",
                hue       = "count",
                palette   = "viridis",
                sizes     = (50, 500),
                edgecolor = "black",
                alpha     = 0.75)
plt.xlabel("Class")
plt.ylabel("Cluster")
plt.title("Cluster vs Class Scatter Plot (Bubble Size = Count)")
plt.xticks(rotation = 45)
plt.grid(True)
plt.legend(title="Count")
plt.savefig("/tmp/Class_Clusters-" + str(n_pca) + "-" + str(n_clusters) + tag + ".png")

# report
log.info("Cluster Centers:") 
centers = kmeans_model.clusterCenters() 
for center in centers: 
  log.info(center)
log.info("Cluster Groups:")
cr.groupBy("cluster").count().show(n_clusters)

# show
#cr.show(truncate=False)

# End --------------------------------------------------------------------------

spark.stop()


