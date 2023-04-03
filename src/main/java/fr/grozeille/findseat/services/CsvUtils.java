package fr.grozeille.findseat.services;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.apache.commons.codec.binary.Hex;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CsvUtils {
    public static List<List<String>> readCsv(File file)  {
        List<List<String>> records = new ArrayList<>();
        boolean containBOM = false;
        try {
            containBOM = isContainBOM(file);
        } catch (IOException e) {
            throw new RuntimeException("Not able to load "+file.getName(), e);
        }

        boolean firstLine = true;
        try (CSVReader csvReader = new CSVReader(new FileReader(file))) {
            String[] values = null;
            while ((values = csvReader.readNext()) != null) {
                if(containBOM && firstLine) {
                    String firstColumn = values[0];
                    byte[] firstColumnBytes = firstColumn.getBytes(StandardCharsets.UTF_8);
                    // remove the BOM
                    firstColumnBytes = Arrays.copyOfRange(firstColumnBytes, 3, firstColumnBytes.length);
                    firstColumn = new String(firstColumnBytes, StandardCharsets.UTF_8);
                    values[0] = firstColumn;
                }
                records.add(Arrays.asList(values));
                firstLine = false;
            }
        } catch (IOException | CsvValidationException e) {
            throw new RuntimeException("Not able to load "+file.getName(), e);
        }

        return records;
    }

    public static  List<List<String>> readCsv(String fileName) {
        URL resource = ConfigService.class.getClassLoader().getResource(fileName);
        try {
            return readCsv(new File(resource.toURI()));
        } catch (URISyntaxException e) {
            throw new RuntimeException("Not able to load "+fileName, e);
        }
    }

    private static  boolean isContainBOM(File file) throws IOException {
        boolean result = false;

        byte[] bom = new byte[3];
        try (InputStream is = new FileInputStream(file)) {

            // read 3 bytes of a file.
            is.read(bom);

            // BOM encoded as ef bb bf
            String content = new String(Hex.encodeHex(bom));
            if ("efbbbf".equalsIgnoreCase(content)) {
                result = true;
            }

        }

        return result;
    }

}
