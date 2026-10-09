package com.Lomikel.Parquet;

import com.Lomikel.Utils.LomikelException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.example.ExampleParquetWriter;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.MessageTypeParser;

/** Offline file:// regression for row boundaries and fail-fast directory traversal. */
public class ParquetReaderIntegrityTest {
  private static final MessageType SCHEMA = MessageTypeParser.parseMessageType(
      "message alert { optional group diaSource { optional double ra; optional double dec; } " +
      "optional group diaObject { optional binary diaObjectId (STRING); } }");
  private static final MessageType REVERSED_SCHEMA = MessageTypeParser.parseMessageType(
      "message alert { optional group diaObject { optional binary diaObjectId (STRING); } " +
      "optional group diaSource { optional double ra; optional double dec; } }");
  private static void check(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }
  private static Path file(java.nio.file.Path dir, String name, String... ids) throws IOException {
    return fileWithSchema(dir, name, SCHEMA, ids);
  }
  private static Path fileWithSchema(java.nio.file.Path dir, String name, MessageType schema, String... ids) throws IOException {
    Path path = new Path(dir.resolve(name).toUri());
    SimpleGroupFactory factory = new SimpleGroupFactory(schema);
    try (ParquetWriter<Group> writer = ExampleParquetWriter.builder(path).withType(schema).build()) {
      for (String id : ids) {
        Group row = factory.newGroup();
        row.addGroup("diaSource").append("ra", 1.0).append("dec", 2.0);
        if (id != null) row.addGroup("diaObject").append("diaObjectId", id);
        writer.write(row);
      }
    }
    return path;
  }
  private static final class Reader extends ParquetReader {
    final List<String> ids = new ArrayList<>();
    final List<String> ra = new ArrayList<>();
    Reader() throws Exception { super("file:/"); }
    @Override protected void beginRecord() { props().clear(); }
    @Override protected void endRecord() {
      if (props().containsKey("diaObject.diaObjectId")) {
        ids.add(props().get("diaObject.diaObjectId").iterator().next());
        ra.add(props().get("diaSource.ra").iterator().next());
      }
    }
  }
  private static void rows() throws Exception {
    java.nio.file.Path dir = Files.createTempDirectory("parquet-integrity-");
    Reader reader = new Reader();
    reader.processFile(file(dir, "single.parquet", "one"));
    check(reader.ids.equals(List.of("one")), "single row lost: " + reader.ids);
    reader.processFile(file(dir, "multi.parquet", "two", null, "three"));
    check(reader.ids.equals(List.of("one", "two", "three")), "final or missing-ID row leaked: " + reader.ids);
    check(reader.ra.size() == 3, "nested group values not collected");
    reader.processFile(fileWithSchema(dir, "id-first.parquet", REVERSED_SCHEMA, "four"));
    check(reader.ids.equals(List.of("one", "two", "three", "four")), "ID-first row lost or duplicated: " + reader.ids);
  }
  private static void strict() throws Exception {
    java.nio.file.Path dir = Files.createTempDirectory("parquet-strict-");
    Reader reader = new Reader();
    try { reader.processDirStrict(dir.resolve("missing").toString(), "parquet");
      throw new AssertionError("strict traversal accepted a missing directory");
    } catch (IOException expected) { /* fail closed */ }
    reader.processOptionalDirStrict(dir.resolve("missing").toString(), "parquet");
    check(reader.ids.isEmpty(), "missing optional day processed data");
    java.nio.file.Path notDirectory = Files.writeString(dir.resolve("not-a-directory"), "x");
    try { reader.processOptionalDirStrict(notDirectory.toString(), "parquet");
      throw new AssertionError("existing file accepted as optional directory");
    } catch (IOException expected) { /* only absence is optional */ }
    java.nio.file.Path nested = Files.createDirectory(dir.resolve("nested"));
    file(nested, "ok.parquet", "ok");
    reader.processDirStrict(dir.toString(), "parquet");
    check(reader.ids.equals(List.of("ok")), "valid nested file not read: " + reader.ids);
    Files.writeString(nested.resolve("broken.parquet"), "not parquet");
    int prior = reader.ids.size();
    try { reader.processDirStrict(dir.toString(), "parquet");
      throw new AssertionError("corrupt file accepted");
    } catch (IOException | RuntimeException expected) { /* fail closed */ }
    check(reader.ids.size() <= prior + 1, "continued past a read failure");
    try { reader.processOptionalDirStrict(dir.toString(), "parquet");
      throw new AssertionError("optional traversal hid a corrupt existing file");
    } catch (IOException | RuntimeException expected) { /* existing data remains strict */ }
  }
  public static void main(String[] args) throws Exception {
    rows();
    strict();
    System.out.println("ParquetReaderIntegrityTest PASS");
  }
}
