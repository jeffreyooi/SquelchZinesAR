package com.squelchzines.squelchzinesar.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;

import com.squelchzines.squelchzinesar.R;

public class PermissionUtil {

    public static final int REQUEST_CODE_CAMERA = 0x01;

    public static void requestCameraPermission(Activity activity) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, Manifest.permission.CAMERA)) {
                showRequestPermissionRationaleDialog(activity);
            } else {
                ActivityCompat.requestPermissions(activity,
                        new String[] { Manifest.permission.CAMERA }, REQUEST_CODE_CAMERA);
            }
        }
    }

    private static void showRequestPermissionRationaleDialog(Context context) {
        AlertDialog rationaleDialog = new AlertDialog.Builder(context)
                .setTitle(R.string.permission_required)
                .setMessage(R.string.permission_rationale)
                .setPositiveButton(
                        R.string.go_to_settings,
                        (dialog, which) -> launchPermissionSettings(context))
                .create();
        rationaleDialog.show();
    }

    private static void launchPermissionSettings(Context context) {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", context.getPackageName(), null));
        context.startActivity(intent);
    }
}
