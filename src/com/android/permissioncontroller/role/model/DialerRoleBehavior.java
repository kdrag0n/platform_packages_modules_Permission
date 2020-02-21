/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.permissioncontroller.role.model;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.utils.CollectionUtils;
import com.android.permissioncontroller.role.utils.PackageUtils;

import java.util.Objects;

/**
 * Class for behavior of the dialer role.
 *
 * @see com.android.settings.applications.DefaultAppSettings
 * @see com.android.settings.applications.defaultapps.DefaultPhonePreferenceController
 * @see com.android.settings.applications.defaultapps.DefaultPhonePicker
 */
public class DialerRoleBehavior implements RoleBehavior {

    private static final AppOp sAccessCallAudioOp = new AppOp(AppOpsManager.OPSTR_ACCESS_CALL_AUDIO,
                                                              null,
                                                              AppOpsManager.MODE_ALLOWED);

    @Override
    public boolean isAvailableAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
        return telephonyManager.isVoiceCapable();
    }

    @Override
    public void prepareApplicationPreferenceAsUser(@NonNull Role role,
            @NonNull Preference preference, @NonNull ApplicationInfo applicationInfo,
            @NonNull UserHandle user, @NonNull Context context) {
        TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
        String systemPackageName = telecomManager.getSystemDialerPackage();
        if (Objects.equals(applicationInfo.packageName, systemPackageName)) {
            preference.setSummary(R.string.default_app_system_default);
        } else {
            preference.setSummary(null);
        }
    }

    @Nullable
    @Override
    public CharSequence getConfirmationMessage(@NonNull Role role, @NonNull String packageName,
            @NonNull Context context) {
        return EncryptionUnawareConfirmationMixin.getConfirmationMessage(role, packageName,
                context);
    }

    @Nullable
    @Override
    public String getFallbackHolder(@NonNull Role role, @NonNull Context context) {
        return CollectionUtils.firstOrNull(role.getDefaultHolders(context));
    }

    @Override
    public boolean isVisibleAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        return context.getResources().getBoolean(R.bool.config_showDialerRole);
    }

    @Override
    public void grant(@NonNull Role role, @NonNull String packageName, @NonNull Context context) {
        PackageInfo packageInfo = PackageUtils.getPackageInfo(packageName,
                                                              PackageManager.GET_PERMISSIONS,
                                                              context);
        if (packageInfo == null) {
            return;
        }
        for (String permission : packageInfo.requestedPermissions) {
            if (Objects.equals(android.Manifest.permission.ACCESS_CALL_AUDIO, permission)) {
                sAccessCallAudioOp.grant(packageName, context);
                break;
            }
        }
    }

    @Override
    public void revoke(@NonNull Role role, @NonNull String packageName, @NonNull Context context) {
        sAccessCallAudioOp.revoke(packageName, context);
    }
}
