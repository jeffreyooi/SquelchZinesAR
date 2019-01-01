package com.squelchzines.squelchzinesar;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

import com.google.ar.core.AugmentedImage;
import com.google.ar.core.Pose;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ExternalTexture;
import com.google.ar.sceneform.rendering.ModelRenderable;

public class AugmentedVideoNode extends AnchorNode {

    private static final String TAG = AugmentedVideoNode.class.getSimpleName();

    private static final String QUAD_MODEL = "quad.sfb";
    private static final String VIDEO_TEXTURE = "videoTexture";
    // Height of video in world space
    private static final float VIDEO_HEIGHT_METERS = 0.1f;

    // Augmented image represented by this node.
    private AugmentedImage mAugmentedImage;

    // Texture for rendering video.
    private ExternalTexture mTexture;

    // Quad for rendering the video on.
    private static ModelRenderable mQuadRenderable;

    private MediaPlayer mMediaPlayer;

    public AugmentedVideoNode(Context context, int resId) {
        mTexture = new ExternalTexture();
        if (mQuadRenderable == null) {
            ModelRenderable.builder()
                    .setSource(context, Uri.parse(QUAD_MODEL))
                    .build()
                    .thenAccept(renderable -> {
                        mQuadRenderable = renderable;
                        renderable.getMaterial().setExternalTexture(VIDEO_TEXTURE, mTexture);
                    })
                    .exceptionally(throwable -> {
                        Log.e(TAG, "Unable to load video renderable", throwable);
                        return null;
                    });
        }
        createMediaPlayer(context, resId);
    }

    public void setImage(AugmentedImage image) {
        this.mAugmentedImage = image;
        setAnchor(image.createAnchor(image.getCenterPose()));

        Vector3 localScale = new Vector3();
        Node node;

        node = new Node();
        node.setParent(this);

        Quaternion localRotation =
                Quaternion.axisAngle(new Vector3(-1.0f, 0.0f, 0.0f), 90);

        float videoWidth = mMediaPlayer.getVideoWidth();
        float videoHeight = mMediaPlayer.getVideoHeight();
        localScale.set(VIDEO_HEIGHT_METERS * (videoWidth / videoHeight), VIDEO_HEIGHT_METERS, 1.0f);
        node.setLocalPosition(new Vector3(0.0f, 0.0f, 0.05f));
        node.setLocalScale(localScale);
        node.setLocalRotation(localRotation);

        if (!mMediaPlayer.isPlaying()) {
            mMediaPlayer.start();
            mTexture.getSurfaceTexture()
                    .setOnFrameAvailableListener(surfaceTexture -> {
                        node.setRenderable(mQuadRenderable);
                        mTexture.getSurfaceTexture().setOnFrameAvailableListener(null);
                    });
        } else {
            node.setRenderable(mQuadRenderable);
        }
    }

    public AugmentedImage getImage() {
        return mAugmentedImage;
    }

    private void createMediaPlayer(Context context, int resId) {
        mMediaPlayer = MediaPlayer.create(context, resId);
        mMediaPlayer.setSurface(mTexture.getSurface());
        mMediaPlayer.setLooping(true);
    }

    void stop() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();
        stop();
    }
}
