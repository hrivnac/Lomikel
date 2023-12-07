package com.astrolabsoftware.FinkBrowser.Parquet;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.format.converter.ParquetMetadataConverter;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.io.MessageColumnIO;
import org.apache.parquet.io.ColumnIOFactory;
import org.apache.parquet.io.RecordReader;
import org.apache.parquet.example.data.simple.convert.GroupRecordConverter;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroup;
import org.apache.parquet.schema.GroupType;

public class Test {

  public static void main(String[] args) {
    readParquetFile();
    }
  
  private static void readParquetFile() {
    Configuration conf = new Configuration();
    Path path = new Path("/user/hrivnac/2021_05_01_part-00001-25a8dbcc-1a3c-428b-9eeb-087566a78bbd.c000.snappy.parquet");
    try {
      ParquetMetadata readFooter = ParquetFileReader.readFooter(conf, path, ParquetMetadataConverter.NO_FILTER);
      MessageType schema = readFooter.getFileMetaData().getSchema();
      ParquetFileReader r = new ParquetFileReader(conf, path, readFooter);
      PageReadStore pages = null;
      while (null != (pages = r.readNextRowGroup())) {
        final long rows = pages.getRowCount();
        System.out.println("Number of rows: " + rows);      
        final MessageColumnIO columnIO = new ColumnIOFactory().getColumnIO(schema);
        final RecordReader<Group> recordReader = columnIO.getRecordReader(pages, new GroupRecordConverter(schema));
        String sTemp = "";
        Group g;
        SimpleGroup sg;
        GroupType type;
        int n;
        while ((g = recordReader.read()) != null) {
          if (g instanceof SimpleGroup) {
            sg = (SimpleGroup)g;
            type = sg.getType();
            n = type.getFieldCount();
            for (int i = 0; i < n; i++) {
              System.out.println(type.getFieldName(i) + " " + g.getFieldRepetitionCount(i) + " " + type.getType(i));
              //System.out.println(g.getString(i, 0));
              }
            }
          else {
            System.out.println(g.getClass());
            }
          }
        }
      }
    catch (Exception e) {
      e.printStackTrace();
      }
    }
    
  } 