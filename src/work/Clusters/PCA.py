from pyspark.sql import SparkSession
from pyspark.sql import functions as F
from pyspark.sql.functions import col
from pyspark.sql.functions import mean
from pyspark.sql.functions import stddev
from pyspark.sql.functions import udf
from pyspark.sql.functions import lit
from pyspark.sql.functions import split
from pyspark.sql.functions import pandas_udf
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

from fink_filters.classification import extract_fink_classification

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

# Parameters -------------------------------------------------------------------


dataFn = "/user/julien.peloton/archive/science/year=2024/month=10"
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
          
n_sample = 10000000
n_pca = 25
n_clusters = 3
silhouette = False
cluster_features = "pca_features"

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

df = df.filter(df.lc_features_g.isNotNull())\
       .filter(df.lc_features_r.isNotNull())\
       .limit(n_sample)        

# Classification ---------------------------------------------------------------

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

# Converting lc_features arrays into columns -----------------------------------
      
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
        + [col(f"lc_features_g.{feat}").alias(f"g_{feat}") for feat in feature_names]\
        + [col(f"lc_features_r.{feat}").alias(f"r_{feat}") for feat in feature_names]

df = df.select(*columns)\
       .drop("lc_features_g", "lc_features_r")      

mean_values = df.select([mean(col(c)).alias(c) for c in df.columns if c != "class"])\
                .collect()[0]\
                .asDict()

mean_values = {k: (v if v is not None and not math.isnan(v) else 0) for k, v in mean_values.items()}

df = df.na.fill(mean_values)

# Standardisation --------------------------------------------------------------

cols = [c for c in df.columns if c != "class"]

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
with open("/tmp/scaler_params.json", "w") as f:
  json.dump(scaler_params, f)

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

# use n_pca for variance about 80%


# End --------------------------------------------------------------------------

spark.stop()


