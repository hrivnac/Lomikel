from fink_filters.classification import extract_fink_classification
from pyspark.sql import SparkSession
from pyspark.sql.functions import pandas_udf, col
from pyspark.sql.types import StringType
import numpy as np

# Initialize Spark session
spark = SparkSession.builder.appName("Fink Classification").getOrCreate()

def find_max(row):
    unique, pos = np.unique(row, return_inverse=True)
    counts = np.bincount(pos)
    maxpos = counts.argmax()
    return unique[maxpos]

@pandas_udf(StringType())
def max_occurrence(classcol):
    return classcol.apply(find_max)

# Load DataFrame from parquet file
df = spark.read.format("parquet").load("/user/julien.peloton/archive/science/year=2024/month=10")

# Define the arguments for classification extraction
args = [
    "cdsxmatch",
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
    "tracklet"
]

# Extract classifications and select relevant columns
df = df.withColumn("class", extract_fink_classification(*args))
df = df.select(["objectId", "candidate.magpsf", "candidate.jd", "candidate.fid", "class"])

# Group by objectId and collect lists of specified columns
df_grouped = df.groupBy("objectId").agg(
    {
        "magpsf": "collect_list",
        "jd": "collect_list",
        "fid": "collect_list",
        "class": "collect_list"
    }
)

# Apply the max_occurrence function to the collected class lists
df_grouped = df_grouped.withColumn("maxclass", max_occurrence(col("collect_list(class)")))

# Show the resulting DataFrame (optional)
df_grouped.show()


