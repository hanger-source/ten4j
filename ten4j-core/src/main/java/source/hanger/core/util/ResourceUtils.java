package source.hanger.core.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Slf4j
public class ResourceUtils {

    /**
     * 从 classpath 中指定路径下获取所有符合文件扩展名的资源的 InputStream。
     *
     * @param resourcePath  Classpath 资源路径（例如 "graph"）
     * @param fileExtension 目标文件的扩展名（例如 ".json"）
     * @return 符合条件的资源的 InputStream 列表
     * @throws IOException 如果在访问文件或 JAR 时发生 I/O 错误
     */
    public static List<InputStream> getResourceInputStreams(String resourcePath, String fileExtension)
            throws IOException {
        List<InputStream> inputStreams = new ArrayList<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = ResourceUtils.class.getClassLoader();
        }

        Enumeration<URL> resources = classLoader.getResources(resourcePath);

        while (resources.hasMoreElements()) {
            URL resourceUrl = resources.nextElement();
            log.debug("ResourceUtils: Found resource URL: {}", resourceUrl);

            if (resourceUrl.getProtocol().equals("file")) {
                String decodedPath = URLDecoder.decode(resourceUrl.getFile(), "UTF-8");
                File folder = new File(decodedPath);
                if (folder.exists() && folder.isDirectory()) {
                    log.debug("ResourceUtils: Accessing directory: {}", folder.getAbsolutePath());
                    File[] files = folder.listFiles((dir, name) -> name.endsWith(fileExtension));
                    if (files != null) {
                        for (File file : files) {
                            inputStreams.add(new java.io.FileInputStream(file));
                        }
                    }
                } else {
                    log.warn("ResourceUtils: File URL {} is not an existing directory. Skipping.", resourceUrl);
                }
            } else if (resourceUrl.getProtocol().equals("jar")) {
                String rawJarPath = resourceUrl.getPath();
                int bangIndex = rawJarPath.indexOf("!");
                if (bangIndex == -1) {
                    log.warn("ResourceUtils: Invalid JAR URL format: {}. Skipping.", rawJarPath);
                    continue;
                }
                String jarFilePath = rawJarPath.substring(5, bangIndex); // Remove "file:" prefix
                String decodedJarFilePath = URLDecoder.decode(jarFilePath, "UTF-8");

                log.debug("ResourceUtils: Attempting to open JAR file: {}", decodedJarFilePath);
                try (JarFile jarFile = new JarFile(decodedJarFilePath)) {
                    Enumeration<JarEntry> entries = jarFile.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String entryName = entry.getName();
                        // Check if the entry is within the specified resource path and has the correct
                        // extension
                        if (entryName.startsWith(resourcePath + "/") && entryName.endsWith(fileExtension)) {
                            log.debug("ResourceUtils: Found entry in JAR: {}", entryName);
                            inputStreams.add(jarFile.getInputStream(entry));
                        }
                    }
                } catch (IOException e) {
                    log.error("ResourceUtils: Error opening or reading JAR file {}: {}", decodedJarFilePath,
                            e.getMessage());
                }
            } else {
                log.warn("ResourceUtils: Unsupported resource protocol: {}. Skipping URL: {}",
                        resourceUrl.getProtocol(), resourceUrl);
            }
        }
        return inputStreams;
    }
}
