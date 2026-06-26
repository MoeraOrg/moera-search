package org.moera.search.media;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import jakarta.inject.Inject;

import org.moera.lib.http.Response;
import org.moera.lib.node.exception.MoeraNodeException;
import org.moera.lib.node.types.AvatarImage;
import org.moera.lib.node.types.MediaAttachment;
import org.moera.lib.node.types.PrivateMediaFileInfo;
import org.moera.lib.node.types.RemoteMediaInfo;
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
import org.moera.search.data.MediaLocation;
import org.moera.search.util.BodyUtil;
import org.moera.search.util.DigestingOutputStream;
import org.moera.search.util.MediaAttachmentUtil;
import org.moera.search.util.ParametrizedLock;
import org.moera.search.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

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
        String remoteNodeName, String mediaId, Response responseBody, TemporaryFile tmpFile, int maxSize
    ) throws MoeraNodeException {
        String contentType = Objects.toString(responseBody.contentType(), null);
        if (contentType == null) {
            throw new MoeraNodeException("Response has no Content-Type");
        }
        Long contentLength = responseBody.contentLength() >= 0 ? responseBody.contentLength() : null;
        log.debug("Content length: {} bytes", contentLength);
        try {
            DigestingOutputStream out = MediaOperations.transfer(
                responseBody.bodyStream(), tmpFile.outputStream(), contentLength, maxSize
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
        String nodeName,
        String carte,
        String id,
        String grant,
        TemporaryFile tmpFile,
        int maxSize
    ) throws MoeraNodeException {
        var result = new AtomicReference<TemporaryMediaFile>();
        var node = carte != null ? nodeApi.at(nodeName, carte) : nodeApi.at(nodeName);
        node.getPrivateMedia(
            id, null, null, grant, null,
            responseBody -> result.set(receiveMediaFile(nodeName, id, responseBody, tmpFile, maxSize))
        );
        return result.get();
    }

    private PrivateMediaFileInfo getPrivateMediaInfo(
        String nodeName,
        String carte,
        String id,
        String grant
    ) throws MoeraNodeException {
        var node = carte != null ? nodeApi.at(nodeName, carte) : nodeApi.at(nodeName);
        return node.getPrivateMediaInfo(id, grant);
    }

    private byte[] getPrivateMediaDigest(
        String nodeName,
        String carte,
        PrivateMediaFileInfo info
    ) throws MoeraNodeException {
        if (info == null || info.getId() == null) {
            return null;
        }

        String id = info.getId();
        var digest = database.read(() -> cacheMediaDigestRepository.getDigest(nodeName, id));
        if (digest != null) {
            return digest;
        }

        int maxSize = config.getMedia().getVerifyMaxSize();
        if (info.getDigest() != null && info.getSize() > maxSize) {
            digest = Util.base64decode(info.getDigest());
            if (digest != null) {
                byte[] digestToStore = digest;
                database.writeNoResult(() -> cacheMediaDigestRepository.storeDigest(nodeName, id, digestToStore));
            }
            return digest;
        }

        try (var tmp = mediaOperations.tmpFile()) {
            var tmpMedia = getPrivateMedia(nodeName, carte, id, info.getGrant(), tmp, maxSize);
            database.writeNoResult(() -> cacheMediaDigestRepository.storeDigest(nodeName, id, tmpMedia.digest()));
            return tmpMedia.digest();
        }
    }

    private byte[] getPrivateMediaDigest(String nodeName, String carte, MediaAttachment attachment)
        throws MoeraNodeException {
        if (attachment.getMedia() != null) {
            return getPrivateMediaDigest(nodeName, carte, attachment.getMedia());
        }

        RemoteMediaInfo remoteMedia = attachment.getRemoteMedia();
        if (
            remoteMedia == null
            || ObjectUtils.isEmpty(remoteMedia.getNodeName())
            || ObjectUtils.isEmpty(remoteMedia.getMediaId())
        ) {
            return null;
        }

        String mediaNodeName = remoteMedia.getNodeName();
        var digest = database.read(() -> cacheMediaDigestRepository.getDigest(mediaNodeName, remoteMedia.getMediaId()));
        if (digest != null) {
            return digest;
        }

        if (
            remoteMedia.getDigest() != null
            && remoteMedia.getSize() != null
            && remoteMedia.getSize() > config.getMedia().getVerifyMaxSize()
        ) {
            digest = Util.base64decode(remoteMedia.getDigest());
            if (digest != null) {
                byte[] digestToStore = digest;
                database.writeNoResult(() ->
                    cacheMediaDigestRepository.storeDigest(mediaNodeName, remoteMedia.getMediaId(), digestToStore)
                );
            }
            return digest;
        }

        var info = getPrivateMediaInfo(
            mediaNodeName,
            carteForMediaNode(nodeName, carte, mediaNodeName),
            remoteMedia.getMediaId(),
            remoteMedia.getGrant()
        );
        if (info != null && info.getGrant() == null) {
            info.setGrant(remoteMedia.getGrant());
        }

        return getPrivateMediaDigest(
            mediaNodeName,
            carteForMediaNode(nodeName, carte, mediaNodeName),
            info
        );
    }

    public Function<MediaAttachment, byte[]> privateMediaDigestGetter(String nodeName, String carte) {
        return attachment -> {
            try {
                return getPrivateMediaDigest(nodeName, carte, attachment);
            } catch (MoeraNodeException e) {
                throw new MoeraNodeUncheckedException(e);
            }
        };
    }

    private MediaFile previewPrivateMedia(
        String nodeName,
        String carte,
        String id,
        String grant
    ) throws MoeraNodeException {
        try (var tmp = mediaOperations.tmpFile()) {
            var tmpMedia = getPrivateMedia(nodeName, carte, id, grant, tmp, config.getMedia().getPreviewMaxSize());
            return mediaOperations.createPreview(tmpMedia.contentType(), tmp.path());
        } catch (IOException e) {
            throw new MoeraNodeLocalStorageException(
                "Error creating a private media preview %s: %s".formatted(id, e.getMessage())
            );
        }
    }

    private PrivateMediaFileInfo resolvePrivateMediaInfo(
        String nodeName,
        String carte,
        MediaAttachment attachment
    ) throws MoeraNodeException {
        if (attachment.getMedia() != null) {
            return attachment.getMedia();
        }

        RemoteMediaInfo remoteMedia = attachment.getRemoteMedia();
        if (
            remoteMedia == null
            || ObjectUtils.isEmpty(remoteMedia.getNodeName())
            || ObjectUtils.isEmpty(remoteMedia.getMediaId())
        ) {
            return null;
        }

        var info = new PrivateMediaFileInfo();
        info.setId(remoteMedia.getMediaId());
        info.setHash(remoteMedia.getHash());
        info.setDigest(remoteMedia.getDigest());
        info.setMimeType(remoteMedia.getMimeType());
        info.setWidth(remoteMedia.getWidth());
        info.setHeight(remoteMedia.getHeight());
        info.setSize(remoteMedia.getSize() != null ? remoteMedia.getSize() : 0);
        info.setTitle(remoteMedia.getTitle());
        info.setAttachment(remoteMedia.getAttachment());
        info.setGrant(remoteMedia.getGrant());

        if (info.getHash() != null && info.getDigest() != null && info.getMimeType() != null && info.getSize() > 0) {
            return info;
        }

        info = getPrivateMediaInfo(
            remoteMedia.getNodeName(),
            carteForMediaNode(nodeName, carte, remoteMedia.getNodeName()),
            remoteMedia.getMediaId(),
            remoteMedia.getGrant()
        );

        return info;
    }

    private String carteForMediaNode(String localNodeName, String carte, String mediaNodeName) {
        return Objects.equals(localNodeName, mediaNodeName) ? carte : null;
    }

    public interface MediaPreviewSaver {

        void save(String mediaFileId, String mediaNodeName, String mediaId);

    }

    public void previewAndSavePrivateMedia(
        String nodeName,
        Supplier<String> carteSupplier,
        Body body,
        List<MediaAttachment> media,
        Supplier<MediaLocation> mediaPreviewGetter,
        MediaPreviewSaver mediaPreviewSaver
    ) {
        var attachment = BodyUtil.findMediaForPreview(body, media);
        var mediaId = attachment != null ? MediaAttachmentUtil.mediaId(attachment) : null;
        var mediaNodeName = attachment != null ? MediaAttachmentUtil.nodeName(attachment, nodeName) : null;
        var mediaPreview = database.read(mediaPreviewGetter);
        if (Objects.equals(mediaPreview, mediaId != null ? new MediaLocation(mediaNodeName, mediaId) : null)) {
            return;
        }
        database.writeNoResult(() -> {
            try {
                MediaFile mediaFile = null;
                if (attachment != null) {
                    String carte = carteSupplier.get();
                    var info = resolvePrivateMediaInfo(nodeName, carte, attachment);
                    mediaFile = info != null
                        ? previewPrivateMedia(
                            mediaNodeName,
                            carteForMediaNode(nodeName, carte, mediaNodeName),
                            info.getId(),
                            info.getGrant()
                        )
                        : null;
                }
                if (mediaFile != null) {
                    mediaPreviewSaver.save(mediaFile.getId(), mediaNodeName, mediaId);
                } else {
                    mediaPreviewSaver.save(null, null, null);
                }
            } catch (MoeraNodeException e) {
                throw new MoeraNodeUncheckedException(e);
            }
        });
    }

}
