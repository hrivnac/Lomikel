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

# Define the arguments for classification extraction ---------------------------

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

# Extract classifications and select relevant columns --------------------------

df = df.withColumn("class", extract_fink_classification(*args))

# Convert lc_features arrays into columns --------------------------------------
       
df = df.select(col("class"),
               col("tracklet"),
               col("lc_features_g.mean"                             ).alias("g_mean"                             ),
               col("lc_features_g.weighted_mean"                    ).alias("g_weighted_mean"                    ),
               col("lc_features_g.standard_deviation"               ).alias("g_standard_deviation"               ),
               col("lc_features_g.median"                           ).alias("g_median"                           ),
               col("lc_features_g.amplitude"                        ).alias("g_amplitude"                        ),
               col("lc_features_g.beyond_1_std"                     ).alias("g_beyond_1_std"                     ),
               col("lc_features_g.cusum"                            ).alias("g_cusum"                            ),
               col("lc_features_g.inter_percentile_range_10"        ).alias("g_inter_percentile_range_10"        ),
               col("lc_features_g.kurtosis"                         ).alias("g_kurtosis"                         ),
               col("lc_features_g.linear_trend"                     ).alias("g_linear_trend"                     ),
               col("lc_features_g.linear_trend_sigma"               ).alias("g_linear_trend_sigma"               ),
               col("lc_features_g.linear_trend_noise"               ).alias("g_linear_trend_noise"               ),
               col("lc_features_g.linear_fit_slope"                 ).alias("g_linear_fit_slope"                 ),
               col("lc_features_g.linear_fit_slope_sigma"           ).alias("g_linear_fit_slope_sigma"           ),
               col("lc_features_g.linear_fit_reduced_chi2"          ).alias("g_linear_fit_reduced_chi2"          ),
               col("lc_features_g.magnitude_percentage_ratio_40_5"  ).alias("g_magnitude_percentage_ratio_40_5"  ),
               col("lc_features_g.magnitude_percentage_ratio_20_10" ).alias("g_magnitude_percentage_ratio_20_10" ),
               col("lc_features_g.maximum_slope"                    ).alias("g_maximum_slope"                    ),
               col("lc_features_g.median_absolute_deviation"        ).alias("g_median_absolute_deviation"        ),
               col("lc_features_g.median_buffer_range_percentage_10").alias("g_median_buffer_range_percentage_10"),
               col("lc_features_g.percent_amplitude"                ).alias("g_percent_amplitude"                ),
               col("lc_features_g.mean_variance"                    ).alias("g_mean_variance"                    ),
               col("lc_features_g.anderson_darling_normal"          ).alias("g_anderson_darling_normal"          ),
               col("lc_features_g.chi2"                             ).alias("g_chi2"                             ),
               col("lc_features_g.skew"                             ).alias("g_skew"                             ),
               col("lc_features_g.stetson_K"                        ).alias("g_stetson_K"                        ),
               col("lc_features_r.mean"                             ).alias("r_mean"                             ),
               col("lc_features_r.weighted_mean"                    ).alias("r_weighted_mean"                    ),
               col("lc_features_r.standard_deviation"               ).alias("r_standard_deviation"               ),
               col("lc_features_r.median"                           ).alias("r_median"                           ),
               col("lc_features_r.amplitude"                        ).alias("r_amplitude"                        ),
               col("lc_features_r.beyond_1_std"                     ).alias("r_beyond_1_std"                     ),
               col("lc_features_r.cusum"                            ).alias("r_cusum"                            ),
               col("lc_features_r.inter_percentile_range_10"        ).alias("r_inter_percentile_range_10"        ),
               col("lc_features_r.kurtosis"                         ).alias("r_kurtosis"                         ),
               col("lc_features_r.linear_trend"                     ).alias("r_linear_trend"                     ),
               col("lc_features_r.linear_trend_sigma"               ).alias("r_linear_trend_sigma"               ),
               col("lc_features_r.linear_trend_noise"               ).alias("r_linear_trend_noise"               ),
               col("lc_features_r.linear_fit_slope"                 ).alias("r_linear_fit_slope"                 ),
               col("lc_features_r.linear_fit_slope_sigma"           ).alias("r_linear_fit_slope_sigma"           ),
               col("lc_features_r.linear_fit_reduced_chi2"          ).alias("r_linear_fit_reduced_chi2"          ),
               col("lc_features_r.magnitude_percentage_ratio_40_5"  ).alias("r_magnitude_percentage_ratio_40_5"  ),
               col("lc_features_r.magnitude_percentage_ratio_20_10" ).alias("r_magnitude_percentage_ratio_20_10" ),
               col("lc_features_r.maximum_slope"                    ).alias("r_maximum_slope"                    ),
               col("lc_features_r.median_absolute_deviation"        ).alias("r_median_absolute_deviation"        ),
               col("lc_features_r.median_buffer_range_percentage_10").alias("r_median_buffer_range_percentage_10"),
               col("lc_features_r.percent_amplitude"                ).alias("r_percent_amplitude"                ),
               col("lc_features_r.mean_variance"                    ).alias("r_mean_variance"                    ),
               col("lc_features_r.anderson_darling_normal"          ).alias("r_anderson_darling_normal"          ),
               col("lc_features_r.chi2"                             ).alias("r_chi2"                             ),
               col("lc_features_r.skew"                             ).alias("r_skew"                             ),
               col("lc_features_r.stetson_K"                        ).alias("r_stetson_K"                        ))\
       .drop("lc_features_g")\
       .drop("lc_features_r")
             
df.show()

# End --------------------------------------------------------------------------

spark.stop()


