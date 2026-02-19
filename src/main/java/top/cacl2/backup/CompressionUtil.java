package top.cacl2.backup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.*;

public class CompressionUtil {
    private final int compressionLevel;
    private final int threadCount;
    private final ExecutorService executor;

    public CompressionUtil(int compressionLevel, int threadCount) {
        this.compressionLevel = compressionLevel;
        this.threadCount = Math.max(1, threadCount);
        this.executor = Executors.newFixedThreadPool(this.threadCount);
    }

    public CompressionResult compressDirectory(Path sourceDir, Path outputFile) throws Exception {
        long startTime = System.currentTimeMillis();
        AtomicLong totalBytes = new AtomicLong(0);
        AtomicLong compressedBytes = new AtomicLong(0);

        try (OutputStream fos = Files.newOutputStream(outputFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            
            zos.setLevel(compressionLevel);
            
            ConcurrentLinkedQueue<Path> filesToCompress = new ConcurrentLinkedQueue<>();
            try (var stream = Files.walk(sourceDir)) {
                stream.filter(Files::isRegularFile)
                      .forEach(filesToCompress::add);
            }

            int totalFiles = filesToCompress.size();
            CountDownLatch latch = new CountDownLatch(totalFiles);

            for (Path file : filesToCompress) {
                executor.submit(() -> {
                    try {
                        Path relativePath = sourceDir.relativize(file);
                        String entryName = relativePath.toString().replace('\\', '/');
                        
                        synchronized (zos) {
                            ZipEntry entry = new ZipEntry(entryName);
                            zos.putNextEntry(entry);
                            
                            try (InputStream fis = Files.newInputStream(file)) {
                                byte[] buffer = new byte[8192];
                                int len;
                                while ((len = fis.read(buffer)) > 0) {
                                    zos.write(buffer, 0, len);
                                    totalBytes.addAndGet(len);
                                }
                            }
                            zos.closeEntry();
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to compress file: " + file, e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
        }

        long endTime = System.currentTimeMillis();
        long compressedSize = Files.size(outputFile);
        
        return new CompressionResult(
            outputFile,
            totalBytes.get(),
            compressedSize,
            endTime - startTime,
            threadCount
        );
    }

    public void decompressArchive(Path zipFile, Path targetDir) throws Exception {
        Files.createDirectories(targetDir);
        
        try (InputStream fis = Files.newInputStream(zipFile);
             ZipInputStream zis = new ZipInputStream(fis)) {
            
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path targetPath = targetDir.resolve(entry.getName());
                
                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static class CompressionResult {
        private final Path outputFile;
        private final long originalSize;
        private final long compressedSize;
        private final long durationMs;
        private final int threadsUsed;

        public CompressionResult(Path outputFile, long originalSize, long compressedSize, 
                                 long durationMs, int threadsUsed) {
            this.outputFile = outputFile;
            this.originalSize = originalSize;
            this.compressedSize = compressedSize;
            this.durationMs = durationMs;
            this.threadsUsed = threadsUsed;
        }

        public Path getOutputFile() { return outputFile; }
        public long getOriginalSize() { return originalSize; }
        public long getCompressedSize() { return compressedSize; }
        public long getDurationMs() { return durationMs; }
        public int getThreadsUsed() { return threadsUsed; }
        
        public double getCompressionRatio() {
            return originalSize > 0 ? (double) compressedSize / originalSize : 0;
        }
        
        public String getFormattedSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
