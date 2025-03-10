from pyspark.sql import SparkSession
from pyspark.sql.functions import pandas_udf
from pyspark.sql.functions import col
from pyspark.sql.types import StringType

from fink_filters.classification import extract_fink_classification

import numpy as np

# Max occurence ----------------------------------------------------------------

def find_max(row):
  unique, pos = np.unique(row, return_inverse = True)
  counts = np.bincount(pos)
  maxpos = counts.argmax()
  return unique[maxpos]

@pandas_udf(StringType())
def max_occurrence(classcol):
  return classcol.apply(find_max)
  
# Parameters -------------------------------------------------------------------

dataFn = "/user/julien.peloton/archive/science/year=2024/month=10"
n_sample = 100000

# New session ------------------------------------------------------------------

spark = SparkSession.builder\
                    .appName("Light Curves Extreaction")\
                    .getOrCreate()
                    
log4jLogger = spark._jvm.org.apache.log4j
log = log4jLogger.LogManager.getLogger("LC")
log.info("Starting...")

# Reading Parquet file into DataFrame ------------------------------------------

df = spark.read\
          .format("parquet")\
          .load(dataFn)
          
df = df.limit(n_sample)        

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

# Grouping by objectId and collect lists of specified columns ------------------

df = df.select(["objectId",
                "candidate.magpsf",
                "candidate.jd",
                "candidate.fid",
                "class"])

df_grouped = df.groupBy("objectId")\
               .agg({"magpsf": "collect_list",
                      "jd":    "collect_list",
                      "fid":   "collect_list",
                      "class": "collect_list"})

# Applying the max_occurrence function to the collected class lists ------------

df_grouped = df_grouped.withColumn("maxclass", max_occurrence(col("collect_list(class)")))

# Show -------------------------------------------------------------------------

df_grouped.show(truncate=False)

# End --------------------------------------------------------------------------

spark.stop()

