package com.astrolabsoftware.FinkBrowser.Parquet;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import com.Lomikel.Utils.LomikelException;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.example.ExampleParquetWriter;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.MessageTypeParser;
import org.objenesis.ObjenesisStd;

/** Offline file:/ regression; never initializes or connects JanusGraph. */
public final class ParquetImporterOfflineTest {
  private static final MessageType SCHEMA = MessageTypeParser.parseMessageType(
      "message alert { required binary id (STRING); }");

  private static final class RecordingImporter extends ParquetImporter {
    List<String> ids;
    List<String> lifecycle;
    boolean failNested;
    // Objenesis constructs this without invoking the JanusClient constructor.
    RecordingImporter() { super("not-used-in-offline-test", -1, -1, "create"); }
    @Override public void process(String path) throws IOException, LomikelException {
      if (failNested && path.endsWith("failed.parquet")) {
        throw new IOException("Injected failure in an existing nested file");
      }
      super.process(path);
    }
    @Override protected void processRow(Group row) {
      if (ids == null) { ids = new ArrayList<>(); }
      ids.add(row.getString("id", 0));
    }
    @Override protected boolean timer(String msg, int count, int report, int commit) {
      return false;
    }
    private void event(String name) {
      if (lifecycle == null) { lifecycle = new ArrayList<>(); }
      lifecycle.add(name);
    }
    @Override public void commit() { event("commit"); }
    @Override public void rollback() { event("rollback"); }
    @Override public void close() { event("close"); }
  }

  private static RecordingImporter reader() {
    return new ObjenesisStd().newInstance(RecordingImporter.class);
  }
  private static void check(boolean yes, String message) {
    if (!yes) { throw new AssertionError(message); }
  }
  private static Path file(java.nio.file.Path dir, String name, String... ids) throws IOException {
    Path path = new Path(dir.resolve(name).toUri());
    SimpleGroupFactory factory = new SimpleGroupFactory(SCHEMA);
    try (ParquetWriter<Group> writer = ExampleParquetWriter.builder(path).withType(SCHEMA).build()) {
      for (String id : ids) {
        writer.write(factory.newGroup().append("id", id));
      }
    }
    return path;
  }
  public static void main(String[] args) throws Exception {
    java.nio.file.Path dir = Files.createTempDirectory("parquet-importer-offline-");
    RecordingImporter one = reader();
    one.process(file(dir, "one.parquet", "one").toString());
    check(List.of("one").equals(one.ids), "A single-row group must be imported: " + one.ids);
    RecordingImporter many = reader();
    many.process(file(dir, "many.parquet", "first", "middle", "last").toString());
    check(List.of("first", "middle", "last").equals(many.ids),
          "Last row of a multi-row group must be imported: " + many.ids);
    try {
      reader().process(dir.resolve("missing.parquet").toUri().toString());
      throw new AssertionError("A missing import must not look successful");
    }
    catch (java.io.FileNotFoundException expected) { /* fail closed */ }
    java.nio.file.Path corrupt = Files.writeString(
        Files.createTempDirectory("parquet-corrupt-").resolve("corrupt.parquet"), "not parquet");
    try {
      reader().process(corrupt.toUri().toString());
      throw new AssertionError("Corrupt Parquet input must not look successful");
    }
    catch (IOException | RuntimeException expected) { /* reject corrupt input */ }
    java.nio.file.Path nested = Files.createDirectory(dir.resolve("nested"));
    file(nested, "failed.parquet", "never");
    RecordingImporter failure = reader();
    failure.failNested = true;
    try {
      failure.process(dir.toString());
      throw new AssertionError("A nested I/O failure must abort the import");
    }
    catch (IOException expected) { /* no final commit or success status */ }
    RecordingImporter completed = reader();
    ParquetImporter.importAndClose(completed, dir.resolve("one.parquet").toUri().toString());
    check(List.of("commit", "close").equals(completed.lifecycle),
          "Successful import must commit then close: " + completed.lifecycle);
    RecordingImporter interrupted = reader();
    interrupted.failNested = true;
    try {
      ParquetImporter.importAndClose(interrupted, dir.toString());
      throw new AssertionError("Failed import was treated as success");
    }
    catch (IOException expected) { /* rollback current transaction */ }
    check(List.of("rollback", "close").equals(interrupted.lifecycle),
          "Failed import must rollback and close without final commit: " + interrupted.lifecycle);
    System.out.println("ParquetImporterOfflineTest PASS");
  }
}
