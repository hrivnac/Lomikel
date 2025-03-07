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
       
lc_features = tuple(f"g{i:02d}" for i in range(26)) \
            + tuple(f"r{i:02d}" for i in range(26))

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
                   
df.show()

# End --------------------------------------------------------------------------

spark.stop()


