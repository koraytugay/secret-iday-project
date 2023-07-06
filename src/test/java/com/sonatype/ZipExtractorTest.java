package com.sonatype;

import java.io.File;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class ZipExtractorTest
{
  @Test
  void extractAndDeleteTest() throws Exception {

    // test zip extraction first
    File zipFile = new File(ZipExtractor.class.getResource("test.zip").toURI());
    File extractedDir = ZipExtractor.extract(zipFile);
    assumeTrue(extractedDir.exists() && extractedDir.isDirectory());

    File[] files = extractedDir.listFiles();
    assumeTrue(files != null && files.length == 2);

    checkFileAssumption(files[0], "file-a.txt", "dir-a");
    checkFileAssumption(files[1], "file-a.txt", "dir-a");

    File dir = files[0].isDirectory() ? files[0] : files[1];
    files = dir.listFiles();
    assumeTrue(files != null && files.length == 2);

    checkFileAssumption(files[0], "file-b.txt", "dir-b");
    checkFileAssumption(files[1], "file-b.txt", "dir-b");

    File dir2 = files[0].isDirectory() ? files[0] : files[1];
    files = dir2.listFiles();
    assumeTrue(files != null && files.length == 1);

    checkFileAssumption(files[0], "file-c.txt", "dir-c");

    // test extracted dir deletion
    ZipExtractor.deleteDirectoryWithContent(extractedDir);
    assumeFalse(extractedDir.exists());
  }

  private void checkFileAssumption(File file, String filename, String dirname) {
    if (file.getName().startsWith("file")) {
      assumeTrue(file.isFile() && file.getName().equals(filename));
    }
    else {
      assumeTrue(file.isDirectory() && file.getName().equals(dirname));
    }
  }
}
