/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.client.android;

import org.hawkular.client.android.push.PushClient;
import org.hawkular.client.android.util.Android;

import android.app.Application;
import android.os.StrictMode;

import timber.log.Timber;

public final class HawkularApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        setUpLogging();
        setUpDetections();

        setUpPush();
    }

    private void setUpLogging() {
        if (Android.isDebugging()) {
            Timber.plant(new Timber.DebugTree());
        }
    }

    private void setUpDetections() {
        if (Android.isDebugging()) {
            StrictMode.enableDefaults();
        }
    }

    private void setUpPush() {
        PushClient.of(this).setUpPush();
    }
}
