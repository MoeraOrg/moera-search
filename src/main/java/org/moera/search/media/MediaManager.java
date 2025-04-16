package org.moera.search.media;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.inject.Inject;

import okhttp3.ResponseBody;
import org.moera.lib.node.exception.MoeraNodeException;
import org.moera.lib.node.types.AvatarImage;
import org.moera.search.api.MoeraNodeLocalStorageException;
import org.moera.search.api.NodeApi;
import org.moera.search.config.Config;
import org.moera.search.data.MediaFile;
import org.moera.search.data.MediaFileRepository;
import org.moera.search.api.model.AvatarImageUtil;
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
    private MediaFileRepository mediaFileRepository;

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

        mediaFileLocks.lock(id);
        try {
            // Could appear in the meantime
            mediaFile = mediaFileRepository.findById(id);
            if (mediaFile != null && mediaFile.isExposed()) {
                return mediaFile;
            }

            var tmp = mediaOperations.tmpFile();
            try {
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
            } finally {
                try {
                    Files.deleteIfExists(tmp.path());
                } catch (IOException e) {
                    log.warn("Error removing temporary media file {}: {}", tmp.path(), e.getMessage());
                }
            }
        } finally {
            mediaFileLocks.unlock(id);
        }
    }

    public MediaFile downloadPublicMedia(String nodeName, AvatarImage avatarImage) throws MoeraNodeException {
        if (avatarImage == null) {
            return null;
        }
        return downloadPublicMedia(nodeName, avatarImage.getMediaId(), config.getMedia().getAvatarMaxSize());
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

    public void downloadAvatars(String nodeName, AvatarImage[] avatarImages) throws MoeraNodeException {
        if (avatarImages != null) {
            for (AvatarImage avatarImage : avatarImages) {
                downloadAvatar(nodeName, avatarImage);
            }
        }
    }

}
