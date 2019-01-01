package com.squelchzines.squelchzinesar;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ExternalTexture;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int REQUEST_CODE_CAMERA = 1;
    private static final double MIN_OPENGL_VERSION = 3.0;
    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;

    // Height of video in world space
    private static final float VIDEO_HEIGHT_METERS = 0.2f;

    private Session mArCoreSession;
    private boolean mUserRequestedInstall = true;

    private ArFragment mArFragment;
    private ImageView mFitToScanView;

    @Nullable
    private ModelRenderable mVideoRenderable;
    private MediaPlayer mMediaPlayer;

    // Stores detected augmented image and their respective nodes.
    private final Map<AugmentedImage, Node> mAugmentedImageNodeMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mArFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        mArFragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);

        mFitToScanView = findViewById(R.id.image_view_fit_to_scan);

        ExternalTexture texture = new ExternalTexture();

        mMediaPlayer = MediaPlayer.create(this, R.raw.kickflip);
        mMediaPlayer.setSurface(texture.getSurface());
        mMediaPlayer.setLooping(true);

        ModelRenderable.builder()
                .setSource(this, Uri.parse("quad.sfb"))
                .build()
                .thenAccept(
                        renderable -> {
                            mVideoRenderable = renderable;
                            renderable.getMaterial().setExternalTexture("videoTexture", texture);
                        })
                .exceptionally(
                        throwable -> {
                            Toast.makeText(this, "Unable to load video renderable", Toast.LENGTH_SHORT).show();
                            return null;
                        });

        mArFragment.setOnTapArPlaneListener(
                (hitResult, plane, motionEvent) -> {
                    if (mVideoRenderable == null) {
                        return;
                    }
                    Anchor anchor = hitResult.createAnchor();
                    AnchorNode anchorNode = new AnchorNode(anchor);
                    anchorNode.setParent(mArFragment.getArSceneView().getScene());

                    Node videoNode = new Node();
                    videoNode.setParent(anchorNode);

                    float videoWidth = mMediaPlayer.getVideoWidth();
                    float videoHeight = mMediaPlayer.getVideoHeight();
                    videoNode.setLocalScale(
                            new Vector3(
                                    VIDEO_HEIGHT_METERS * (videoWidth / videoHeight), VIDEO_HEIGHT_METERS, 1.0f));

                    if (!mMediaPlayer.isPlaying()) {
                        mMediaPlayer.start();
                        texture
                                .getSurfaceTexture()
                                .setOnFrameAvailableListener(
                                        (surfaceTexture) -> {
                                            videoNode.setRenderable(mVideoRenderable);
                                            texture.getSurfaceTexture().setOnFrameAvailableListener(null);
                                        }
                                );
                    } else {
                        videoNode.setRenderable(mVideoRenderable);
                    }
                }
        );
    }

    private void initializeArFragment() {
        mArFragment.getArSceneView().setupSession(mArCoreSession);
    }

    private void configureSession() {
        Config config = new Config(mArCoreSession);
        if (!setupPresetImageDb(config)) {
            Toast.makeText(this, "Failed to load", Toast.LENGTH_SHORT).show();
        }
        config.setCloudAnchorMode(Config.CloudAnchorMode.DISABLED);
        config.setFocusMode(Config.FocusMode.AUTO);
        config.setLightEstimationMode(Config.LightEstimationMode.AMBIENT_INTENSITY);
        config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL);
        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
        mArCoreSession.configure(config);
    }

    private boolean setupPresetImageDb(Config config) {
        try {
            InputStream inputStream = getAssets().open("output_db.imgdb");
            AugmentedImageDatabase db = AugmentedImageDatabase.deserialize(mArCoreSession, inputStream);
            config.setAugmentedImageDatabase(db);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to load AugmentedImageDatabase");
            Toast.makeText(this, "Failed to load AugmentedImageDatabase", Toast.LENGTH_LONG)
                    .show();
            return false;
        }
    }

    private void onUpdateFrame(FrameTime frameTime) {
        Frame frame = mArFragment.getArSceneView().getArFrame();

        if (frame == null || frame.getCamera().getTrackingState() != TrackingState.TRACKING) {
            return;
        }

        Collection<AugmentedImage> updatedAugmentedImages =
                frame.getUpdatedTrackables(AugmentedImage.class);

        for (AugmentedImage augmentedImage : updatedAugmentedImages) {
            switch (augmentedImage.getTrackingState()) {
                case PAUSED:
                    break;
                case TRACKING:
                    mFitToScanView.setVisibility(View.GONE);
                    if (!mAugmentedImageNodeMap.containsKey(augmentedImage)) {

                    }
                    break;
                case STOPPED:
                    mAugmentedImageNodeMap.remove(augmentedImage);
                    break;
            }
            if (augmentedImage.getTrackingState() == TrackingState.TRACKING) {
                if (augmentedImage.getIndex() == 0) {
                    // TODO: Show something
                } else if (augmentedImage.getIndex() == 1) {
                    // TODO: Show something
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkOpenGlesVersion();
        requestPermission();
        checkArCore();
        configureSession();
        initializeArFragment();
        try {
            mArCoreSession.resume();
            mArFragment.getArSceneView().resume();
        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
            Toast.makeText(this, "Camera not available. Please restart the app", Toast.LENGTH_LONG)
                    .show();
            mArCoreSession = null;
        }
        if (mAugmentedImageNodeMap.isEmpty()) {
            mFitToScanView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mArCoreSession != null) {
            mArFragment.getArSceneView().pause();
            mArCoreSession.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    private void requestPermission() {
        if (ContextCompat.checkSelfPermission(
                this, PERMISSION_CAMERA) != PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this, PERMISSION_CAMERA)) {
                showRequestPermissionRationaleDialog();
            } else {
                ActivityCompat.requestPermissions(
                        this, new String[] { PERMISSION_CAMERA }, REQUEST_CODE_CAMERA);
            }
        }
    }

    private void launchPermissionSettings() {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    private void showRequestPermissionRationaleDialog() {
        AlertDialog rationaleDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.permission_required)
                .setMessage(R.string.permission_rationale)
                .setPositiveButton(
                        R.string.go_to_settings, (dialog, which) -> launchPermissionSettings())
                .create();
        rationaleDialog.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_CAMERA:
                if (grantResults[0] != PERMISSION_GRANTED) {
                    requestPermission();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void checkOpenGlesVersion() {
        String openGlVersionString =
                ((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE))
                .getDeviceConfigurationInfo()
                .getGlEsVersion();

        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Toast.makeText(this, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private void checkArCore() {
        try {
            if (mArCoreSession == null) {
                switch (ArCoreApk.getInstance().requestInstall(this, mUserRequestedInstall)) {
                    case INSTALLED:
                        mArCoreSession = new Session(this);
                        break;
                    case INSTALL_REQUESTED:
                        mUserRequestedInstall = false;
                        break;
                }
            }
        } catch (UnavailableUserDeclinedInstallationException e) {
            e.printStackTrace();
            Toast.makeText(
                    this, R.string.ar_core_error_user_declined_installation, Toast.LENGTH_SHORT)
                    .show();
        } catch (UnavailableDeviceNotCompatibleException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.ar_core_error_device_not_supported, Toast.LENGTH_SHORT)
                    .show();
        } catch (UnavailableArcoreNotInstalledException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.ar_core_error_not_installed, Toast.LENGTH_SHORT)
                    .show();
        } catch (UnavailableApkTooOldException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.ar_core_error_outdated, Toast.LENGTH_SHORT)
                    .show();
        } catch (UnavailableSdkTooOldException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.ar_core_error_sdk_low, Toast.LENGTH_SHORT)
                    .show();
        }
    }
}
