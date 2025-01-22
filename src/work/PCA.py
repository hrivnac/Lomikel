from pyspark.sql import SparkSession
from pyspark.sql import functions as F
from pyspark.sql.functions import col
from pyspark.sql.functions import mean
from pyspark.sql.functions import stddev
from pyspark.sql.functions import udf
from pyspark.sql.functions import lit
from pyspark.sql.types import DoubleType
from pyspark.ml import Pipeline
from pyspark.ml.feature import PCA
from pyspark.ml.feature import VectorAssembler
from pyspark.ml.linalg import Vectors
from pyspark.ml.clustering import KMeans
from pyspark.ml.evaluation import ClusteringEvaluator
from numpy import array
from math import sqrt

spark = SparkSession.builder.appName("PCA with HBase").getOrCreate()

print("*** DF ***")
mapping = "rowkey STRING :key, " + \
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
df.show()

print("*** VectorAssembler ***")
vecAssembler = VectorAssembler(inputCols=cols, outputCol="features")

print ("*** PCA ***")
pca = PCA(k=3, inputCol="features", outputCol="pcaFeatures")
pipeline = Pipeline(stages=[vecAssembler, pca])
model = pipeline.fit(df)
result = model.transform(df).select("pcaFeatures")
print(result)

print("*** Clustering ***")
#kmeans = KMeans().setK(5).setSeed(1).setFeaturesCol("pcaFeatures").setPredictionCol("cluster")
#kmeans_model = kmeans.fit(result)
#clustered_result = kmeans_model.transform(result)

print("*** Clusters ***")
#clustered_result.show()

print("*** Results ***")
#clustered_result.select("pcaFeatures", "cluster").show(truncate=False)

print("*** Centers ***")
#centers = kmeans_model.clusterCenters()
#for idx, center in enumerate(centers):
#    print(f"Cluster {idx}: {center}")

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


