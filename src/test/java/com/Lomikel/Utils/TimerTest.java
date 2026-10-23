package com.Lomikel.Utils;

/** Offline regression checks for Timer argument validation and reporting. */
public class TimerTest {
  private static void rejectsZeroModulus() {
    try {
      new Timer("test", 0, 1);
      throw new AssertionError("zero modulus accepted");
    } catch (IllegalArgumentException expected) { }
  }

  private static void rejectsZeroCommitFrequency() {
    try {
      new Timer("test", 1, 0);
      throw new AssertionError("zero commit frequency accepted");
    } catch (IllegalArgumentException expected) { }
  }

  private static void preservesDisabledFrequencies() {
    Timer timer = new Timer("test", -2, -3);
    timer.start();
    for (int i = 0; i < 5; i++) {
      if (timer.report()) throw new AssertionError("disabled commit requested");
    }
  }

  private static void preservesPositiveCadence() {
    Timer timer = new Timer("test", 2, 2);
    timer.start();
    for (int i = 1; i <= 6; i++) {
      boolean expectedCommit = i == 4;
      if (timer.report() != expectedCommit) {
        throw new AssertionError("unexpected commit on call " + i);
      }
    }
  }

  public static void main(String[] args) {
    rejectsZeroModulus();
    rejectsZeroCommitFrequency();
    preservesDisabledFrequencies();
    preservesPositiveCadence();
    System.out.println("TimerTest OK");
  }
}
