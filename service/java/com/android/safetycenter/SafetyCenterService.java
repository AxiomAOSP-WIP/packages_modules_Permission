/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.safetycenter;

import static android.Manifest.permission.SEND_SAFETY_CENTER_UPDATE;
import static android.os.Build.VERSION_CODES.TIRAMISU;

import static java.util.Objects.requireNonNull;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.AppOpsManager;
import android.content.Context;
import android.os.Binder;
import android.safetycenter.ISafetyCenterManager;
import android.safetycenter.SafetySourceData;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.internal.annotations.GuardedBy;
import com.android.permission.util.PermissionUtils;
import com.android.server.SystemService;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Service for the safety center.
 *
 * @hide
 */
@Keep
@RequiresApi(TIRAMISU)
public final class SafetyCenterService extends SystemService {

    @NonNull
    private final Object mLock = new Object();
    // TODO(b/206789604): Use persistent storage instead.
    @GuardedBy("mLock")
    @NonNull
    private final Map<Key, SafetySourceData> mSafetySourceDataForKey = new HashMap<>();

    @NonNull
    private final AppOpsManager mAppOpsManager;

    public SafetyCenterService(@NonNull Context context) {
        super(context);
        mAppOpsManager = requireNonNull(context.getSystemService(AppOpsManager.class));
    }

    @Override
    public void onStart() {
        publishBinderService(Context.SAFETY_CENTER_SERVICE, new Stub());
    }

    private static final class Key {
        @NonNull
        private final String mPackageName;
        private final int mUserId;
        @NonNull
        private final String mSafetySourceId;

        private Key(@NonNull String packageName, int userId, @NonNull String safetySourceId) {
            this.mPackageName = packageName;
            this.mUserId = userId;
            this.mSafetySourceId = safetySourceId;
        }

        @NonNull
        private static Key of(@NonNull String packageName, int userId,
                @NonNull String safetySourceId) {
            return new Key(packageName, userId, safetySourceId);
        }

        @Override
        public String toString() {
            return "Key{"
                    + "mPackageName='"
                    + mPackageName
                    + '\''
                    + ", mUserId="
                    + mUserId
                    + ", mSafetySourceId='"
                    + mSafetySourceId
                    + '\''
                    + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key)) return false;
            Key key = (Key) o;
            return mUserId == key.mUserId && mPackageName.equals(key.mPackageName)
                    && mSafetySourceId.equals(key.mSafetySourceId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mPackageName, mUserId, mSafetySourceId);
        }
    }

    private final class Stub extends ISafetyCenterManager.Stub {
        @Override
        public void sendSafetyCenterUpdate(@NonNull String packageName, @UserIdInt int userId,
                @NonNull SafetySourceData safetySourceData) {
            mAppOpsManager.checkPackage(Binder.getCallingUid(), packageName);
            PermissionUtils.enforceCrossUserPermission(userId, false, "sendSafetyCenterUpdate",
                    getContext());
            getContext().enforceCallingOrSelfPermission(SEND_SAFETY_CENTER_UPDATE,
                    "sendSafetyCenterUpdate");
            // TODO(b/205706756): Security: check certs?
            // TODO(b/203098016): Implement merging logic.
            synchronized (mLock) {
                mSafetySourceDataForKey.put(Key.of(packageName, userId, safetySourceData.getId()),
                        safetySourceData);
            }
        }

        @Override
        @Nullable
        public SafetySourceData getLastSafetyCenterUpdate(@NonNull String packageName,
                @UserIdInt int userId,
                @NonNull String safetySourceId) {
            mAppOpsManager.checkPackage(Binder.getCallingUid(), packageName);
            PermissionUtils.enforceCrossUserPermission(userId, false, "getLastSafetyCenterUpdate",
                    getContext());
            getContext().enforceCallingOrSelfPermission(SEND_SAFETY_CENTER_UPDATE,
                    "getLastSafetyCenterUpdate");
            // TODO(b/205706756): Security: check certs?
            synchronized (mLock) {
                return mSafetySourceDataForKey.get(Key.of(packageName, userId, safetySourceId));
            }
        }
    }
}