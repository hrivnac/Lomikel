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
          
extra_cols = ["magpsf", "sigmapsf", "magnr", "sigmagnr", "magzpsci"]
rowkey_start = "ZTF24"
n_sample = 10000000
n_pca = 5
n_clusters = 3
read_sample = True
add_extra_cols = False
silhouette = False
classify = True
cluster_features = "pca_features"

# New session ------------------------------------------------------------------

spark = SparkSession.builder\
                    .appName("PCA CLustering")\
                    .getOrCreate()
                    
log4jLogger = spark._jvm.org.apache.log4j
log = log4jLogger.LogManager.getLogger("PCA")
log.info("Starting...")

# Read Parquet file into DataFrame ---------------------------------------------

df = spark.read\
          .format("parquet")\
          .load(dataFn)

df = df.filter(df.lc_features_g.isNotNull())\
       .filter(df.lc_features_r.isNotNull())\
       .limit(n_sample)

# Convert lc_features arrays into columns --------------------------------------
       
df_split = df.select("*",  # Keep all existing columns
                     col("lc_features_g.mean").alias("mean"),
                     col("lc_features_g.weighted_mean").alias("weighted_mean"),
                     col("lc_features_g.standard_deviation").alias("standard_deviation"),
                     col("lc_features_g.median").alias("median"),
                     col("lc_features_g.amplitude").alias("amplitude"),
                     col("lc_features_g.beyond_1_std").alias("beyond_1_std"),
                     col("lc_features_g.cusum").alias("cusum"),
                     col("lc_features_g.inter_percentile_range_10").alias("inter_percentile_range_10"),
                     col("lc_features_g.kurtosis").alias("kurtosis"),
                     col("lc_features_g.linear_trend").alias("linear_trend"),
                     col("lc_features_g.linear_trend_sigma").alias("linear_trend_sigma"),
                     col("lc_features_g.linear_trend_noise").alias("linear_trend_noise"),
                     col("lc_features_g.linear_fit_slope").alias("linear_fit_slope"),
                     col("lc_features_g.linear_fit_slope_sigma").alias("linear_fit_slope_sigma"),
                     col("lc_features_g.linear_fit_reduced_chi2").alias("linear_fit_reduced_chi2"),
                     col("lc_features_g.magnitude_percentage_ratio_40_5").alias("magnitude_percentage_ratio_40_5"),
                     col("lc_features_g.magnitude_percentage_ratio_20_10").alias("magnitude_percentage_ratio_20_10"),
                     col("lc_features_g.maximum_slope").alias("maximum_slope"),
                     col("lc_features_g.median_absolute_deviation").alias("median_absolute_deviation"),
                     col("lc_features_g.median_buffer_range_percentage_10").alias("median_buffer_range_percentage_10"),
                     col("lc_features_g.percent_amplitude").alias("percent_amplitude"),
                     col("lc_features_g.mean_variance").alias("mean_variance"),
                     col("lc_features_g.anderson_darling_normal").alias("anderson_darling_normal"),
                     col("lc_features_g.chi2").alias("chi2"),
                     col("lc_features_g.skew").alias("skew"),
                     col("lc_features_g.stetson_K").alias("stetson_K")).drop("lc_features_g") 
                   
df.show()

# End --------------------------------------------------------------------------

spark.stop()


