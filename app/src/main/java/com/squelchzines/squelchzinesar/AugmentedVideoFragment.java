package com.squelchzines.squelchzinesar;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Config;
import com.google.ar.core.Session;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.ux.ArFragment;

import java.io.IOException;
import java.io.InputStream;

public class AugmentedVideoFragment extends ArFragment {

    private static final String TAG = AugmentedVideoFragment.class.getSimpleName();

    private static final String IMAGE_DATABASE = "output_db.imgdb";

    private static final double MIN_OPENGL_VERSION = 3.0;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        checkOpenGlesVersion(context);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        // Hide plane discovery since we are only doing image detection.
        getPlaneDiscoveryController().hide();
        getPlaneDiscoveryController().setInstructionView(null);
        getArSceneView().getPlaneRenderer().setEnabled(false);

        return view;
    }

    @Override
    protected Config getSessionConfiguration(Session session) {
        Config config = super.getSessionConfiguration(session);
        if (!setupPresetImageDb(config, session)) {
            Toast.makeText(getContext(), "Failed to load AugmentedImageDatabase", Toast.LENGTH_SHORT)
                    .show();
        }
        return config;
    }

    public boolean setupPresetImageDb(Config config, Session session) {
        try {
            InputStream is = getContext().getAssets().open(IMAGE_DATABASE);
            AugmentedImageDatabase db = AugmentedImageDatabase.deserialize(session, is);
            config.setAugmentedImageDatabase(db);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to load AugmentedImageDatabase");
            Toast.makeText(getContext(), "Failed to load AugmentedImageDatabase", Toast.LENGTH_LONG)
                    .show();
            return false;
        }
    }

    protected void addOnUpdateListener(Scene.OnUpdateListener onUpdateListener) {
        getArSceneView().getScene().addOnUpdateListener(onUpdateListener);
    }

    private void checkOpenGlesVersion(Context context) {
        String openGlVersionString =
                ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE))
                        .getDeviceConfigurationInfo()
                        .getGlEsVersion();

        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Toast.makeText(context, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_SHORT)
                    .show();
        }
    }
}
