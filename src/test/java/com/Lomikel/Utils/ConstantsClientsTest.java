package com.Lomikel.Utils;

import com.Lomikel.HBaser.HBaseEvaluatorFunctions;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Offline regression checks for the legacy pi field and its calculation clients. */
public class ConstantsClientsTest {
  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static String source(Path root, String relative) throws Exception {
    return new String(Files.readAllBytes(root.resolve(relative)), StandardCharsets.UTF_8);
  }

  private static void legacyFieldContract(Path root) throws Exception {
    Field field = Constants.class.getField("π");
    check(field.getType() == double.class, "legacy field type changed");
    check(Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()) &&
          !Modifier.isFinal(field.getModifiers()), "legacy field must remain public, static, mutable");
    check(field.isAnnotationPresent(Deprecated.class), "legacy field must be @Deprecated");
    String text = source(root, "src/java/com/Lomikel/Utils/Constants.java");
    check(text.contains("@deprecated") && text.contains("Math.PI"),
          "field documentation must direct callers to Math.PI");
    check(Double.doubleToLongBits(Constants.π) == Double.doubleToLongBits(Math.PI),
          "legacy field must initially equal Math.PI");
  }

  private static void calculationClientsUseImmutablePi(Path root) throws Exception {
    String[] clients = {
      "src/java/com/Lomikel/ElasticSearcher/ESClient.java",
      "src/java/com/Lomikel/HBaser/HBaseEvaluatorFunctions.java",
      "src/groovy/com/astrolabsoftware/FinkBrowser/Januser/FinkGremlinRecipiesGT.groovy"
    };
    String[] formulas = {
      "ang * Math.PI * 6371008.7714 / 180.0",
      "d < delta * Math.PI / 180.0",
      "ang * 6371.0087714 * Math.PI / 180"
    };
    for (int i = 0; i < clients.length; i++) {
      String text = source(root, clients[i]);
      check(!text.contains("Constants.π") && !text.matches("(?s).*\\bπ\\b.*"),
            clients[i] + " still references the mutable field");
      check(text.contains(formulas[i]), clients[i] + " changed its conversion formula");
    }
  }

  private static void mutationDoesNotChangeAngularClient() {
    double original = Constants.π;
    try {
      boolean expected = HBaseEvaluatorFunctions.isNear(10, 20, 10, 20, 1);
      check(expected, "identical directions with positive radius must be near");
      Constants.π = 0;
      check(HBaseEvaluatorFunctions.isNear(10, 20, 10, 20, 1) == expected,
            "angular calculation must not depend on mutable legacy field");
    } finally {
      Constants.π = original;
    }
  }

  public static void main(String[] args) throws Exception {
    Path root = Paths.get(args[0]);
    legacyFieldContract(root);
    calculationClientsUseImmutablePi(root);
    mutationDoesNotChangeAngularClient();
    System.out.println("PASS ConstantsClientsTest");
  }
}
