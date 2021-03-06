/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.client.android.auth;

import java.io.Serializable;
import java.net.URI;
import java.net.URL;
import java.util.UUID;

import org.hawkular.client.android.activity.LoginActivity;
import org.hawkular.client.android.util.Preferences;
import org.jboss.aerogear.android.authorization.AuthzModule;
import org.jboss.aerogear.android.authorization.oauth2.OAuth2AuthzSession;
import org.jboss.aerogear.android.core.Callback;
import org.jboss.aerogear.android.pipe.http.HeaderAndBody;
import org.jboss.aerogear.android.pipe.http.HttpException;
import org.jboss.aerogear.android.pipe.http.HttpProvider;
import org.jboss.aerogear.android.pipe.http.HttpRestProvider;
import org.jboss.aerogear.android.pipe.module.AuthorizationFields;
import org.jboss.aerogear.android.pipe.module.ModuleFields;
import org.jboss.aerogear.android.store.DataManager;
import org.jboss.aerogear.android.store.generator.IdGenerator;
import org.jboss.aerogear.android.store.sql.SQLStore;
import org.jboss.aerogear.android.store.sql.SQLStoreConfiguration;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

/**
 * Authorization activity.
 *
 * Performs all related to authorization user operations, including accepting server host and port information
 * and triggering the OAuth flow.
 */

public class ModifiedAuthzModule implements AuthzModule {

    Context applicationContext;
    private SQLStore<Session> sessionStore;

    public ModifiedAuthzModule(Context context) {
        applicationContext = context;
    }

    @Override
    public boolean isAuthorized() {
        return true;
    }

    @Override
    public boolean hasCredentials() {
        Session storedAccount = sessionStore.read("hawkular");
        if(storedAccount!=null)
        {
            return true;
        }
        return false;
    }

    @Override
    public void requestAccess(final Activity activity, final Callback<String> callback) {

        Exception exception;
        String json;
        if (hasCredentials()) {
            Session storedAccount = sessionStore.read("hawkular");
            final String key = storedAccount.getKey();
            final String secret = storedAccount.getSecret();
            JSONObject obj = new JSONObject();

            try {
                obj.put("key", key);
                obj.put("secret", secret);
                callback.onSuccess(obj.toString());
            } catch (JSONException e) {
                callback.onFailure(e);
            }
        } else {

            AsyncTask<Void, Void, HeaderAndBody> task = new AsyncTask<Void, Void, HeaderAndBody>() {
                private Exception exception;
                String json;

                @Override
                protected HeaderAndBody doInBackground(Void... params) {
                    HeaderAndBody result;
                    try {
                        HttpProvider provider =
                                new HttpRestProvider(new URL(((LoginActivity) (activity)).backendUrl.toString()
                                        + "/secret-store/v1/tokens/create"));
                        provider.setDefaultHeader("Accept", "application/json");
                        provider.setDefaultHeader("Authorization", "Basic "
                                + buildLoginData(((LoginActivity) (activity)).username,
                                ((LoginActivity) (activity)).password));
                        result = provider.post("");
                        Log.d("Called with POST to: ", new String(provider.getUrl().toString()));
                        Log.d("Result of the POST: ", new String(result.getBody()));

                        json = new String(result.getBody());

                    } catch (Exception e) {
                        Log.e(ModifiedAuthzModule.class.getSimpleName(),
                                "Error with Login", e);
                        exception = e;
                        return null;
                    }

                    return result;
                }

                @Override
                protected void onPostExecute(HeaderAndBody headerAndBody) {
                    if (exception == null) {
                        callback.onSuccess(new String(headerAndBody.getBody()));
                    } else {
                        callback.onFailure(exception);
                    }
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }


    private String buildLoginData(String username, String password) {
        String cred = username + ":" + password;
        return new String(Base64.encode(cred.getBytes(), Base64.NO_WRAP));
    }

    @Override
    public boolean refreshAccess() {
        return true;
    }

    @Override
    public void deleteAccount() {
        openSessionStore().remove("hawkular");
    }

    @Override
    public AuthorizationFields getAuthorizationFields(URI requestUri, String method, byte[] requestBody) {
        AuthorizationFields fields = new AuthorizationFields();
        Session storedAccount = openSessionStore().read("hawkular");
        String cred = new String(Base64.encode((storedAccount.getKey() + ":"
                + storedAccount.getKey()).getBytes(), Base64.NO_WRAP));
        fields.addHeader("Authorization", "Basic " + cred);

        return fields;
    }

    @Override
    public ModuleFields loadModule(URI relativeURI, String httpMethod, byte[] requestBody) {
        AuthorizationFields authzFields = getAuthorizationFields(relativeURI, httpMethod, requestBody);
        ModuleFields moduleFields = new ModuleFields();
        moduleFields.setHeaders(authzFields.getHeaders());
        moduleFields.setQueryParameters(authzFields.getQueryParameters());
        return moduleFields;
    }

    @Override
    public boolean handleError(HttpException exception) {
        return false;
    }

    private SQLStore<Session> openSessionStore() {
        DataManager.config("sessionStore", SQLStoreConfiguration.class)
                .forClass(OAuth2AuthzSession.class)
                .withContext(applicationContext)
                .withIdGenerator(new IdGenerator() {
                    @Override
                    public String generate() {
                        return UUID.randomUUID().toString();
                    }
                }).store();

        sessionStore  = (SQLStore<Session>) DataManager.getStore("sessionStore");
        sessionStore.openSync();
        return sessionStore;
    }

    public void addAccount(Session session)
    {
        openSessionStore().save(session);
    }


}



