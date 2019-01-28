package com.squelchzines.squelchzinesar;

import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedImage;
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
import com.google.ar.sceneform.FrameTime;
import com.squelchzines.squelchzinesar.util.PermissionUtil;

import java.io.IOException;
import java.util.Collection;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private Session mArCoreSession;
    private boolean mUserRequestedInstall = true;

    private AugmentedVideoFragment mArFragment;
    private ImageView mFitToScanView;
    private TextView mFitToScanTextView;
    private ImageButton mHelpButton;

    private AugmentedVideoNode mCurrentNode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mArFragment = (AugmentedVideoFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        mArFragment.addOnUpdateListener(this::onUpdateFrame);

        mFitToScanView = findViewById(R.id.image_view_fit_to_scan);
        mFitToScanTextView = findViewById(R.id.text_view_fit_to_scan);
        mHelpButton = findViewById(R.id.image_button_help);
    }

    private void configureSession() {
        Config config = mArFragment.getSessionConfiguration(mArCoreSession);
        // Disable cloud anchor mode because we are not using multi device.
        config.setCloudAnchorMode(Config.CloudAnchorMode.DISABLED);
        // Enable auto focus of camera
        config.setFocusMode(Config.FocusMode.AUTO);
        // Set light estimation mode to adapt to surrounding lighting.
        config.setLightEstimationMode(Config.LightEstimationMode.AMBIENT_INTENSITY);
        // Configure frame update to use latest image obtained from camera.
        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
        mArCoreSession.configure(config);
        mArFragment.getArSceneView().setupSession(mArCoreSession);
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
                case TRACKING:
                    mFitToScanView.setVisibility(View.GONE);
                    mFitToScanTextView.setVisibility(View.GONE);
                    if (mCurrentNode == null || !mCurrentNode.getImage().equals(augmentedImage)) {
                        if (mCurrentNode != null) {
                            mCurrentNode.stop();
                            mArFragment.getArSceneView().getScene().removeChild(mCurrentNode);
                        }

                        if (augmentedImage.getIndex() == 0) {
                            AugmentedVideoNode node = new AugmentedVideoNode(this, R.raw.kickflip);
                            node.setImage(augmentedImage);
                            mArFragment.getArSceneView().getScene().addChild(node);
                            mCurrentNode = node;
                        } else if (augmentedImage.getIndex() == 1) {
                            AugmentedVideoNode node = new AugmentedVideoNode(this, R.raw.chicken);
                            node.setImage(augmentedImage);
                            mArFragment.getArSceneView().getScene().addChild(node);
                            mCurrentNode = node;
                        }
                    }

                    break;
                case STOPPED:
                    if (mCurrentNode != null) {
                        mArFragment.getArSceneView().getScene().removeChild(mCurrentNode);
                    }
                    break;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        requestPermission();
        checkArCore();
        configureSession();
        try {
            mArCoreSession.resume();
            mArFragment.getArSceneView().resume();
        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
            Toast.makeText(this, "Camera not available. Please restart the app", Toast.LENGTH_LONG)
                    .show();
            mArCoreSession = null;
        }
        if (mCurrentNode == null) {
            mFitToScanView.setVisibility(View.VISIBLE);
            mFitToScanTextView.setVisibility(View.VISIBLE);
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
    }

    private void requestPermission() {
        PermissionUtil.requestCameraPermission(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case PermissionUtil.REQUEST_CODE_CAMERA:
                if (grantResults[0] != PERMISSION_GRANTED) {
                    requestPermission();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
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
