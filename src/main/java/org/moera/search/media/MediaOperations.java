package org.moera.search.media;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;

import java.awt.Dimension;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import com.drew.imaging.FileType;
import com.drew.imaging.FileTypeDetector;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import org.apache.commons.io.input.BoundedInputStream;
import org.moera.lib.crypto.CryptoUtil;
import org.moera.lib.util.LogUtil;
import org.moera.search.config.Config;
import org.moera.search.data.Database;
import org.moera.search.data.MediaFile;
import org.moera.search.data.MediaFileRepository;
import org.moera.search.global.RequestCounter;
import org.moera.search.job.Jobs;
import org.moera.search.job.JobsManagerInitializedEvent;
import org.moera.search.util.DigestingOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@Component
public class MediaOperations {

    public static final String TMP_DIR = "tmp";
    private static final String PUBLIC_DIR = "public";

    private static final Logger log = LoggerFactory.getLogger(MediaOperations.class);

    @Inject
    private RequestCounter requestCounter;

    @Inject
    private Database database;

    @Inject
    private Config config;

    @Inject
    private MediaFileRepository mediaFileRepository;

    @Inject
    private Jobs jobs;

    @PostConstruct
    public void init() throws MediaPathNotSetException {
        if (ObjectUtils.isEmpty(config.getMedia().getPath())) {
            throw new MediaPathNotSetException("Path not set");
        }
        try {
            Path path = FileSystems.getDefault().getPath(config.getMedia().getPath());
            if (!Files.exists(path)) {
                throw new MediaPathNotSetException("Not found");
            }
            if (!Files.isDirectory(path)) {
                throw new MediaPathNotSetException("Not a directory");
            }
            if (!Files.isWritable(path)) {
                throw new MediaPathNotSetException("Not writable");
            }
            path = path.resolve(MediaOperations.TMP_DIR);
            if (!Files.exists(path)) {
                try {
                    Files.createDirectory(path);
                } catch (FileAlreadyExistsException e) {
                    // ok
                } catch (Exception e) {
                    throw new MediaPathNotSetException("Cannot create tmp/ subdirectory: " + e.getMessage());
                }
            }
        } catch (InvalidPathException e) {
            throw new MediaPathNotSetException("Path is invalid");
        }
    }

    public TemporaryFile tmpFile() {
        while (true) {
            Path path;
            do {
                path = FileSystems.getDefault().getPath(
                    config.getMedia().getPath(), TMP_DIR, CryptoUtil.token().substring(0, 16)
                );
            } while (Files.exists(path));
            try {
                return new TemporaryFile(path, Files.newOutputStream(path, CREATE));
            } catch (IOException e) {
                // next try
            }
        }
    }

    public Path getPath(MediaFile mediaFile) {
        return FileSystems.getDefault().getPath(config.getMedia().getPath(), mediaFile.getFileName());
    }

    public Path getPublicServingPath() {
        return FileSystems.getDefault().getPath(config.getMedia().getPath(), PUBLIC_DIR);
    }

    public Path getPublicServingPath(MediaFile mediaFile) {
        return getPublicServingPath().resolve(mediaFile.getFileName());
    }

    public void createPublicServingLink(MediaFile mediaFile) throws IOException {
        Path servingPath = getPublicServingPath(mediaFile);
        if (!Files.exists(servingPath, LinkOption.NOFOLLOW_LINKS)) {
            Files.createSymbolicLink(servingPath, Paths.get("..", mediaFile.getFileName()));
        }
    }

    public static DigestingOutputStream transfer(
        InputStream in, OutputStream out, Long contentLength, int maxSize
    ) throws IOException {
        DigestingOutputStream digestingStream = new DigestingOutputStream(out);

        out = digestingStream;
        if (contentLength != null) {
            if (contentLength > maxSize) {
                throw new ThresholdReachedException();
            }
            in = BoundedInputStream.builder().setInputStream(in).setMaxCount(contentLength).get();
        } else {
            out = new BoundedOutputStream(out, maxSize);
        }

        try {
            in.transferTo(out);
        } finally {
            out.close();
        }

        return digestingStream;
    }

    private static Dimension getImageDimension(String contentType, Path path) throws InvalidImageException {
        Iterator<ImageReader> it = ImageIO.getImageReadersByMIMEType(contentType);
        while (it.hasNext()) {
            ImageReader reader = it.next();
            try {
                ImageInputStream stream = new FileImageInputStream(path.toFile());
                reader.setInput(stream);
                int width = reader.getWidth(reader.getMinIndex());
                int height = reader.getHeight(reader.getMinIndex());
                return new Dimension(width, height);
            } catch (IOException e) {
                log.warn("Error reading image file {} (Content-Type: {}): {}",
                        LogUtil.format(path.toString()), LogUtil.format(contentType), e.getMessage());
            } finally {
                reader.dispose();
            }
        }

        throw new InvalidImageException();
    }

    public MediaFile putInPlace(
        String id, String contentType, Path tmpPath, byte[] digest, boolean exposed
    ) throws IOException {
        MediaFile mediaFile = mediaFileRepository.findById(id);
        if (mediaFile == null) {
            if (digest == null) {
                digest = digest(tmpPath);
            }
            if (contentType == null || contentType.startsWith("image/")) {
                contentType = detectContentType(tmpPath, contentType);
            }

            Path mediaPath = FileSystems.getDefault().getPath(
                config.getMedia().getPath(), MimeUtils.fileName(id, contentType)
            );
            Files.move(tmpPath, mediaPath, REPLACE_EXISTING);

            mediaFile = new MediaFile();
            mediaFile.setId(id);
            mediaFile.setMimeType(contentType);
            if (contentType.startsWith("image/")) {
                mediaFile.setDimension(getImageDimension(contentType, mediaPath));
            }
            mediaFile.setOrientation(getImageOrientation(mediaPath));
            mediaFile.setFileSize(Files.size(mediaPath));
            mediaFile.setDigest(digest);
            mediaFile.setExposed(exposed);
            mediaFileRepository.create(mediaFile);

            if (config.getMedia().isDirectServe() && exposed) {
                createPublicServingLink(mediaFile);
            }
        }
        return mediaFile;
    }

    private short getImageOrientation(Path imagePath) {
        short orientation = 1;
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(imagePath.toFile());
            if (metadata != null) {
                Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
                if (directory != null) {
                    orientation = (short) directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
                }
            }
        } catch (MetadataException | IOException | ImageProcessingException e) {
            // Could not get orientation, use default
        }
        return orientation;
    }

    private String detectContentType(Path path, String defaultContentType) throws IOException {
        FileType fileType = FileTypeDetector.detectFileType(new BufferedInputStream(new FileInputStream(path.toFile())));
        return fileType != null ? fileType.getMimeType() : defaultContentType;
    }

    public byte[] digest(MediaFile mediaFile) throws IOException {
        return digest(
            FileSystems.getDefault().getPath(
                config.getMedia().getPath(), MimeUtils.fileName(mediaFile.getId(), mediaFile.getMimeType())
            )
        );
    }

    private static byte[] digest(Path mediaPath) throws IOException {
        DigestingOutputStream out = new DigestingOutputStream(OutputStream.nullOutputStream());
        try (InputStream in = new FileInputStream(mediaPath.toFile())) {
            in.transferTo(out);
        }
        return out.getDigest();
    }

    public ResponseEntity<Resource> serve(MediaFile mediaFile, Boolean download) {
        download = download != null ? download : false;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf(mediaFile.getMimeType()));
        if (download) {
            headers.setContentDisposition(ContentDisposition.attachment().build());
        }
        headers.setAccessControlAllowOrigin("*");

        switch (config.getMedia().getServe().toLowerCase()) {
            default:
            case "stream": {
                headers.setContentLength(mediaFile.getFileSize());
                Path mediaPath = getPath(mediaFile);
                return new ResponseEntity<>(new FileSystemResource(mediaPath), headers, HttpStatus.OK);
            }

            case "accel":
                headers.add("X-Accel-Redirect", config.getMedia().getAccelPrefix() + mediaFile.getFileName());
                return new ResponseEntity<>(headers, HttpStatus.OK);

            case "sendfile": {
                Path mediaPath = getPath(mediaFile);
                headers.add("X-SendFile", mediaPath.toAbsolutePath().toString());
                return new ResponseEntity<>(headers, HttpStatus.OK);
            }
        }
    }

    @Scheduled(fixedDelayString = "PT6H")
    public void purgeUnused() {
        try (var ignored = requestCounter.allot()) {
            try (var ignored2 = database.open()) {
                log.info("Purging unused media files");

                Collection<MediaFile> mediaFiles = database.executeWrite(() -> mediaFileRepository.deleteUnused());
                for (MediaFile mediaFile : mediaFiles) {
                    Path path = getPath(mediaFile);
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        log.warn("Error deleting {}: {}", path, e.getMessage());
                    }
                    if (config.getMedia().isDirectServe() && mediaFile.isExposed()) {
                        Path publicPath = getPublicServingPath(mediaFile);
                        try {
                            Files.deleteIfExists(publicPath);
                        } catch (IOException e) {
                            log.warn("Error deleting {}: {}", publicPath, e.getMessage());
                        }
                    }
                }
            }
        }
    }

    @EventListener(JobsManagerInitializedEvent.class)
    public void prepareDirectServing() throws IOException {
        if (!config.getMedia().isDirectServe()) {
            return;
        }

        Path publicDir = getPublicServingPath();
        if (!Files.exists(publicDir)) {
            Files.createDirectory(publicDir);
            try (var ignored = requestCounter.allot()) {
                try (var ignored2 = database.open()) {
                    jobs.run(PreparePublicDirectServingJob.class, new PreparePublicDirectServingJob.Parameters());
                }
            }
        }
    }

}
