package com.smsforwarder;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.util.Log;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Arrays;
import java.util.List;

public class AutoStartUtils {

    private static final String TAG = "AutoStartUtils";

    public static void checkAndPromptOptionalAutoStart(Context context) {
        try {
            String buildInfo = Build.BRAND.toLowerCase();
            Log.d(TAG, "Checking autostart for brand: " + buildInfo);

            List<Intent> intents = Arrays.asList(
                    new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                    new Intent().setComponent(new ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")),
                    new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
                    new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
                    new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
                    new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
                    new Intent().setComponent(new ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
                    new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
                    new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
                    new Intent().setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
                    new Intent().setComponent(new ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity"))
            );

            PackageManager pm = context.getPackageManager();
            for (Intent intent : intents) {
                List<ResolveInfo> list = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
                if (!list.isEmpty()) {
                    Log.d(TAG, "Found autostart intent: " + intent.getComponent());

                    new MaterialAlertDialogBuilder(context)
                            .setTitle("Background Restriction Detected")
                            .setMessage("Your device manufacturer (" + Build.BRAND + ") aggressively kills background apps.\n\nTo ensure SMS Forwarder runs reliably, please enable 'AutoStart' or allow it to run in the background in the next screen.")
                            .setPositiveButton("Go to Settings", (dialog, which) -> {
                                try {
                                    context.startActivity(intent);
                                } catch (Exception e) {
                                    Log.e(TAG, "Failed to launch autostart settings", e);
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();

                    break; // Only show once for the first matching intent
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception while checking autostart", e);
        }
    }
}
