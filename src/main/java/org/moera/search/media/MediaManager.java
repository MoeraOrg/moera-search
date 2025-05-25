package org.moera.search.media;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import jakarta.inject.Inject;

import okhttp3.ResponseBody;
import org.moera.lib.node.exception.MoeraNodeException;
import org.moera.lib.node.types.AvatarImage;
import org.moera.lib.node.types.MediaAttachment;
import org.moera.lib.node.types.body.Body;
import org.moera.search.api.MoeraNodeLocalStorageException;
import org.moera.search.api.MoeraNodeUncheckedException;
import org.moera.search.api.NodeApi;
import org.moera.search.api.model.AvatarImageUtil;
import org.moera.search.config.Config;
import org.moera.search.data.CacheMediaDigestRepository;
import org.moera.search.data.Database;
import org.moera.search.data.MediaFile;
import org.moera.search.data.MediaFileRepository;
import org.moera.search.util.BodyUtil;
import org.moera.search.util.DigestingOutputStream;
import org.moera.search.util.ParametrizedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MediaManager {

    private static final Logger log = LoggerFactory.getLogger(MediaManager.class);

    @Inject
    private Config config;

    @Inject
    private Database database;

    @Inject
    private MediaFileRepository mediaFileRepository;

    @Inject
    private CacheMediaDigestRepository cacheMediaDigestRepository;

    @Inject
    private NodeApi nodeApi;

    @Inject
    private MediaOperations mediaOperations;

    private final ParametrizedLock<String> mediaFileLocks = new ParametrizedLock<>();

    private TemporaryMediaFile receiveMediaFile(
        String remoteNodeName, String mediaId, ResponseBody responseBody, TemporaryFile tmpFile, int maxSize
    ) throws MoeraNodeException {
        String contentType = Objects.toString(responseBody.contentType(), null);
        if (contentType == null) {
            throw new MoeraNodeException("Response has no Content-Type");
        }
        Long contentLength = responseBody.contentLength() >= 0 ? responseBody.contentLength() : null;
        log.debug("Content length: {} bytes", contentLength);
        try {
            DigestingOutputStream out = MediaOperations.transfer(
                responseBody.byteStream(), tmpFile.outputStream(), contentLength, maxSize
            );
            return new TemporaryMediaFile(out.getHash(), contentType, out.getDigest());
        } catch (ThresholdReachedException e) {
            throw new MoeraNodeLocalStorageException(
                "Media %s at %s reports a wrong size or larger than %d bytes"
                    .formatted(mediaId, remoteNodeName, maxSize)
            );
        } catch (IOException e) {
            throw new MoeraNodeLocalStorageException(
                "Error downloading media %s: %s".formatted(mediaId, e.getMessage())
            );
        }
    }

    private TemporaryMediaFile getPublicMedia(
        String nodeName, String id, TemporaryFile tmpFile, int maxSize
    ) throws MoeraNodeException {
        var result = new AtomicReference<TemporaryMediaFile>();
        nodeApi.at(nodeName).getPublicMedia(id, null, null, responseBody ->
            result.set(receiveMediaFile(nodeName, id, responseBody, tmpFile, maxSize))
        );
        return result.get();
    }

    public MediaFile downloadPublicMedia(String nodeName, String id, int maxSize) throws MoeraNodeException {
        if (id == null) {
            return null;
        }

        MediaFile mediaFile = mediaFileRepository.findById(id);
        if (mediaFile != null && mediaFile.isExposed()) {
            return mediaFile;
        }

        try (var ignored = mediaFileLocks.lock(id)) {
            // Could appear in the meantime
            mediaFile = mediaFileRepository.findById(id);
            if (mediaFile != null && mediaFile.isExposed()) {
                return mediaFile;
            }

            try (var tmp = mediaOperations.tmpFile()) {
                var tmpMedia = getPublicMedia(nodeName, id, tmp, maxSize);
                if (!tmpMedia.mediaFileId().equals(id)) {
                    log.warn("Public media {} has hash {}", id, tmpMedia.mediaFileId());
                    return null;
                }
                mediaFile = mediaOperations.putInPlace(id, tmpMedia.contentType(), tmp.path(), null, true);

                return mediaFile;
            } catch (IOException e) {
                throw new MoeraNodeLocalStorageException(
                    "Error storing public media %s: %s".formatted(id, e.getMessage())
                );
            }
        }
    }

    public void downloadAvatar(String nodeName, AvatarImage avatarImage) throws MoeraNodeException {
        int maxSize = config.getMedia().getAvatarMaxSize();

        String id = avatarImage != null ? avatarImage.getMediaId() : null;
        if (id == null) {
            return;
        }

        if (AvatarImageUtil.getMediaFile(avatarImage) != null) {
            return;
        }
        MediaFile mediaFile = mediaFileRepository.findById(id);
        if (mediaFile != null && mediaFile.isExposed()) {
            AvatarImageUtil.setMediaFile(avatarImage, mediaFile);
            return;
        }

        AvatarImageUtil.setMediaFile(avatarImage, downloadPublicMedia(nodeName, id, maxSize));
    }

    public interface AvatarSaver {

        void save(String avatarId, String shape);

    }

    public void downloadAndSaveAvatar(String nodeName, AvatarImage avatar, AvatarSaver avatarSaver) {
        database.writeNoResult(() -> {
            try {
                downloadAvatar(nodeName, avatar);
            } catch (MoeraNodeException e) {
                throw new MoeraNodeUncheckedException(e);
            }
        });
        if (avatar != null && AvatarImageUtil.getMediaFile(avatar) != null) {
            database.writeNoResult(() ->
                avatarSaver.save(AvatarImageUtil.getMediaFile(avatar).getId(), avatar.getShape())
            );
        }
    }

    private TemporaryMediaFile getPrivateMedia(
        String nodeName, String carte, String id, TemporaryFile tmpFile, int maxSize
    ) throws MoeraNodeException {
        var result = new AtomicReference<TemporaryMediaFile>();
        nodeApi.at(nodeName, carte).getPrivateMedia(
            id, null, null, responseBody -> result.set(receiveMediaFile(nodeName, id, responseBody, tmpFile, maxSize))
        );
        return result.get();
    }

    private byte[] getPrivateMediaDigest(String nodeName, String carte, String id) throws MoeraNodeException {
        var digest = database.read(() -> cacheMediaDigestRepository.getDigest(nodeName, id));
        if (digest != null) {
            return digest;
        }

        try (var tmp = mediaOperations.tmpFile()) {
            var tmpMedia = getPrivateMedia(nodeName, carte, id, tmp, config.getMedia().getVerifyMaxSize());
            database.writeNoResult(() -> cacheMediaDigestRepository.storeDigest(nodeName, id, tmpMedia.digest()));
            return tmpMedia.digest();
        }
    }

    public Function<String, byte[]> privateMediaDigestGetter(String nodeName, String carte) {
        return id -> {
            try {
                return getPrivateMediaDigest(nodeName, carte, id);
            } catch (MoeraNodeException e) {
                throw new MoeraNodeUncheckedException(e);
            }
        };
    }

    private MediaFile previewPrivateMedia(String nodeName, String carte, String id) throws MoeraNodeException {
        try (var tmp = mediaOperations.tmpFile()) {
            var tmpMedia = getPrivateMedia(nodeName, carte, id, tmp, config.getMedia().getPreviewMaxSize());
            return mediaOperations.createPreview(tmpMedia.contentType(), tmp.path());
        } catch (IOException e) {
            throw new MoeraNodeLocalStorageException(
                "Error creating a private media preview %s: %s".formatted(id, e.getMessage())
            );
        }
    }

    public void previewAndSavePrivateMedia(
        String nodeName,
        Supplier<String> carteSupplier,
        Body body,
        List<MediaAttachment> media,
        Consumer<MediaFile> mediaSaver
    ) {
        var info = BodyUtil.findMediaForPreview(body, media);
        if (info == null) {
            return;
        }
        database.writeNoResult(() -> {
            try {
                var mediaFile = previewPrivateMedia(nodeName, carteSupplier.get(), info.getId());
                if (mediaFile != null) {
                    mediaSaver.accept(mediaFile);
                }
            } catch (MoeraNodeException e) {
                throw new MoeraNodeUncheckedException(e);
            }
        });
    }

}
