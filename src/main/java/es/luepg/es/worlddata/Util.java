package es.luepg.es.worlddata;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author elmexl
 * Created on 28.05.2019.
 */
public class Util {
    static String reverse(String s) {
        return new StringBuilder(s).reverse().toString();
    }

    static String toCamel(String s) {
        if (s.isEmpty()) {
            return "";
        }

        if (s.length() == 1) {
            return s.toUpperCase();
        }

        StringBuffer resultPlaceHolder = new StringBuffer(s.length());

        Stream.of(s.split("[ _]")).forEach(stringPart -> {
            char[] charArray = stringPart.toLowerCase().toCharArray();
            charArray[0] = Character.toUpperCase(charArray[0]);
            resultPlaceHolder.append(new String(charArray));
        });

        return resultPlaceHolder.toString().trim();
    }

    static boolean isIntList(Collection<String> values) {
        if (values.isEmpty()) return false;
        try {
            for (String val : values) {
                Integer.parseInt(val);
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    static boolean isListOf(Collection<String> values, String... requiredValues) {
        if (values.isEmpty()) return false;
        for (String ee : requiredValues) {
            if (values.contains(ee))
                return true;
        }
        return false;
    }

    static String greatestCommonPrefix(String a, String b) {
        int minLength = Math.min(a.length(), b.length());
        for (int i = 0; i < minLength; i++) {
            if (a.charAt(i) != b.charAt(i)) {
                return a.substring(0, i);
            }
        }
        return a.substring(0, minLength);
    }

    public static void writeFile(File f, String s) throws IOException {
        if (!f.exists()) {
            if (!f.getParentFile().exists()) {
                f.getParentFile().mkdirs();
            }
        }

        Files.write(f.toPath(), s.getBytes(StandardCharsets.UTF_8));
    }

    static String packageToPath(String s) {
        return s.replace('.', '/');
    }

    static Path getResource(ClassLoader cl, String path) throws URISyntaxException, IOException {
        URI uri = cl.getResource(path).toURI();

        if ("jar".equals(uri.getScheme())) {
            for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
                if (provider.getScheme().equalsIgnoreCase("jar")) {
                    try {
                        provider.getFileSystem(uri);
                    } catch (FileSystemNotFoundException e) {
                        // in this case we need to initialize it first:
                        provider.newFileSystem(uri, Collections.emptyMap());
                    }
                }
            }
        }
        return Paths.get(uri);
    }
}
