import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class CompressFiles {
    public static void main(String[] args) {
        File directory = new File("C:/temp");
        File compressedFile = new File("C:/temp/compresso.zip");
        if (!compressedFile.exists()) {
            try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(compressedFile))) {
                File[] files = directory.listFiles((f) -> f.length() > 2048);
                if (files != null) {
                    for (File file : files) {
                        try (FileInputStream fis = new FileInputStream(file)) {
                            ZipEntry zipEntry = new ZipEntry(file.getName());
                            zipOut.putNextEntry(zipEntry);
                            byte[] bytes = new byte[1024];
                            int length;
                            while ((length = fis.read(bytes)) >= 0) {
                                zipOut.write(bytes, 0, length);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}