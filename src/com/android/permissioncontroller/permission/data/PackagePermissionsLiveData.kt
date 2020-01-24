/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.permissioncontroller.permission.data

import android.app.Application
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.UserHandle
import com.android.permissioncontroller.permission.utils.Utils

/**
 * LiveData with a map representing the runtime permissions a group requests and all of the
 * installed, non-runtime, normal protection permissions. Key is the group name or
 * NON_RUNTIME_NORMAL_PERMS, value is the requested runtime permissions in that group (or all
 * installed non-runtime normal protection permissions, for NON_RUNTME_NORMAL_PERMS).
 *
 * @param app The current Application
 * @param packageName The name of the package this LiveData will watch for mode changes for
 * @param user The user for whom the packageInfo will be defined
 */
class PackagePermissionsLiveData(
    private val app: Application,
    packageName: String,
    user: UserHandle
) : SmartUpdateMediatorLiveData<Map<String, List<String>>>() {

    companion object {
        const val NON_RUNTIME_NORMAL_PERMS = "nonRuntimeNormalPerms"
    }

    private val packageInfoLiveData = PackageInfoRepository.getPackageInfoLiveData(app, packageName,
        user)

    init {
        addSource(packageInfoLiveData) {
            update()
        }
    }

    override fun update() {
        val packageInfo = packageInfoLiveData.value ?: return
        val permissionMap = mutableMapOf<String, MutableList<String>>()
        for (permName in packageInfo.requestedPermissions) {
            val permInfo = try {
                app.packageManager.getPermissionInfo(permName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                continue
            }

            if (permInfo.flags and PermissionInfo.FLAG_INSTALLED == 0 ||
                permInfo.flags and PermissionInfo.FLAG_REMOVED != 0) {
                continue
            }

            if (packageInfo.isInstantApp && permInfo.protectionFlags and
                PermissionInfo.PROTECTION_FLAG_INSTANT == 0) {
                continue
            }

            if (packageInfo.targetSdkVersion < Build.VERSION_CODES.M &&
                (permInfo.protectionFlags and PermissionInfo.PROTECTION_FLAG_RUNTIME_ONLY) != 0) {
                continue
            }

            // If this permission is a non-runtime, normal permission, add it to the "non runtime"
            // group
            if (permInfo.protection != PermissionInfo.PROTECTION_DANGEROUS) {
                if (permInfo.protection == PermissionInfo.PROTECTION_NORMAL) {
                    val otherPermsList =
                        permissionMap.getOrPut(NON_RUNTIME_NORMAL_PERMS) { mutableListOf() }
                    otherPermsList.add(permInfo.name)
                }
                continue
            }

            val groupName = Utils.getGroupOfPermission(permInfo) ?: permInfo.name
            if (!permissionMap.containsKey(groupName)) {
                permissionMap[groupName] = mutableListOf()
            }
            permissionMap[groupName]?.add(permInfo.name)
        }

        value = permissionMap
    }
}

/**
 * Repository for PackagePermissionsLiveData objects
 * <p> Key value is a string package name and userHandle, value is its corresponding LiveData.
 */
object PackagePermissionsRepository
    : DataRepository<Pair<String, UserHandle>, PackagePermissionsLiveData>() {

    /**
     * Get the PackagePermissionsLiveData associated with the given parameters, creating it if
     * need be.
     *
     * @param app The current application
     * @param packageName The name of the package desired
     * @param user The UserHandle for whom we want the package
     *
     * @return the cached or newly generated PackagePermissionsLiveData
     */
    fun getPackagePermissionsLiveData(
        app: Application,
        packageName: String,
        user: UserHandle
    ):
        PackagePermissionsLiveData {
        return getDataObject(app, packageName to user)
    }

    override fun newValue(app: Application, key: Pair<String, UserHandle>):
        PackagePermissionsLiveData {
        return PackagePermissionsLiveData(app, key.first, key.second)
    }
}