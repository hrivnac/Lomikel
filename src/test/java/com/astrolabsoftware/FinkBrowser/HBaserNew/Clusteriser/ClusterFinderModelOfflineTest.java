package com.astrolabsoftware.FinkBrowser.HBaserNew.Clusteriser;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Checks all shipped ZTF cluster models without a live HBase connection. */
public class ClusterFinderModelOfflineTest {
  private static void require(boolean yes, String message) {
    if (!yes) throw new AssertionError(message);
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 1) throw new IllegalArgumentException("Expected ZTF model data root");
    Path root = Path.of(args[0]);
    ObjectMapper mapper = new ObjectMapper();
    List<Path> models;
    try (Stream<Path> paths = Files.walk(root)) {
      models = paths.filter(p -> p.getFileName().toString().equals("scaler_params.json"))
                    .sorted().toList();
    }
    require(!models.isEmpty(), "no packaged ZTF models found");
    for (Path scaler : models) {
      Path folder = scaler.getParent();
      Path pca = folder.resolve("pca_params.json");
      Path centers = folder.resolve("cluster_centers.json");
      ScalerParams s = mapper.readValue(scaler.toFile(), ScalerParams.class);
      PCAParams p = mapper.readValue(pca.toFile(), PCAParams.class);
      double[][] c = mapper.readValue(centers.toFile(), double[][].class);
      require(s.mean.length == s.std.length && s.mean.length == p.components.length,
              "scaler/PCA input dimensions differ: " + folder);
      require(c.length > 1 && p.components[0].length == c[0].length,
              "PCA/centers output dimensions differ: " + folder);
      ClusterFinder finder = new ClusterFinder(scaler.toString(), pca.toString(), centers.toString());
      int cluster = finder.transformAndPredict(s.mean.clone());
      require(cluster >= -1 && cluster < c.length, "invalid cluster label: " + folder);
      System.out.println("model PASS " + root.relativize(folder));
    }
    System.out.println("ClusterFinderModelOfflineTest PASS " + models.size() + " models");
  }
}
