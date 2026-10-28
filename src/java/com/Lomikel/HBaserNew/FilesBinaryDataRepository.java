package com.Lomikel.HBaserNew;

import com.Lomikel.Utils.Info;

// Java
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>FilesBinaryDataRepository</code> keeps temporary binary data in files.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class FilesBinaryDataRepository extends BinaryDataRepository {

  /** Create.
    * @param repositoryDirectoryFN The directory to store binary data in.
    *                              Use the default directory if <tt>null</tt> or empty. */
	public FilesBinaryDataRepository(String repositoryDirectoryFN) {
	  if (repositoryDirectoryFN != null && !repositoryDirectoryFN.trim().equals("")) {
	    _repdir = repositoryDirectoryFN;
	    }
	  log.info("Storing binary data in " + _repdir);
    new File(_repdir).mkdirs();
	  }

  /** Create. Store binary data in the default directory. */
	public FilesBinaryDataRepository() {
    this(null);
	  }

	/** Cler the repository. */
  public void clear() {
    // TBD
    }

  /** Put in a <tt>byte[]</tt> entry.
    * @param id       The entry id.
    * @param content  The entry content. */
	public void put(String id,
	                byte[] content) {
    Path file = entryPath(id);
	  try {
      try (OutputStream output = Files.newOutputStream(file, StandardOpenOption.CREATE,
             StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)) {
        output.write(content);
        }
      }
    catch (FileNotFoundException e) {
      log.error("Cannot find file " + _repdir + "/" + id, e);
      }
    catch (IOException e) {
      log.error("Cannot write into file " + _repdir + "/" + id);
      }
	  }

	/** Get a <tt>byte[]</tt> entry.
	  * @param  id      The entry id.
    * @return content The entry content. */
	public byte[] get(String id) {
	  byte[] bytes =  null;
    Path file = entryPath(id);
	  try {
      try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
        bytes = input.readAllBytes();
        }
      }
    catch (FileNotFoundException e) {
      log.error("Cannot find file " + _repdir + "/" + id, e);
      }
    catch (IOException e) {
      log.error("Cannot read from file " + _repdir + "/" + id);
      }
	  return bytes;
	  }

  private Path entryPath(String id) {
    if (id == null || id.isEmpty() || id.equals(".") || id.equals("..") ||
        id.indexOf('/') >= 0 || id.indexOf('\\') >= 0 || id.indexOf('\0') >= 0) {
      throw new IllegalArgumentException("Invalid binary data ID");
      }
    return Path.of(_repdir).resolve(id);
    }

	@Override
	public String toString() {
	  // TBD
	  return "TBD";
	  }

	private static String REPDIR = Info.tmp() +"/HBaseClientBinaryDataRepository";

	private String _repdir = REPDIR;

  /** Logging . */
  private static Logger log = LogManager.getLogger(FilesBinaryDataRepository.class);

  }
