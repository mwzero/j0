import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

public class CompressDirectory {
    public static void main(String[] args) {
        String sourcePath = "C:\\temp";
        String zipFilePath = "C:\\temp\\temp_files.zip";
        compressDirectory(sourcePath, zipFilePath);
    }

    public static void compressDirectory(String sourcePath, String zipFilePath) {
        try {
            Files.deleteIfExists(Paths.get(zipFilePath));
            try (FileOutputStream fos = new FileOutputStream(zipFilePath);
                 ZipOutputStream zos = new ZipOutputStream(fos)) {

                File directory = new File(sourcePath);
                File[] files = directory.listFiles();

                if (files != null) {
                    for (File file : files) {
                        if (file.isFile()) {
                            compressFile(file, zos);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void compressFile(File file, ZipOutputStream zos) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            String relativePath = file.getPath().substring("C:\\temp".length() + 1);
            ZipEntry zipEntry = new ZipEntry(relativePath);
            zos.putNextEntry(zipEntry);
            byte[] bytes = new byte[1024];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                zos.write(bytes, 0, length);
            }
            zos.closeEntry();
        }
    }
}