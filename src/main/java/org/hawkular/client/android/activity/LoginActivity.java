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
package org.hawkular.client.android.activity;

import java.net.URL;
import java.util.List;

import org.hawkular.client.android.R;
import org.hawkular.client.android.backend.BackendClient;
import org.hawkular.client.android.backend.model.Environment;
import org.hawkular.client.android.backend.model.Persona;
import org.hawkular.client.android.util.Ports;
import org.hawkular.client.android.util.Preferences;
import org.hawkular.client.android.util.Urls;
import org.jboss.aerogear.android.core.Callback;
import org.jboss.aerogear.android.pipe.callback.AbstractActivityCallback;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.widget.Button;
import android.widget.EditText;
import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import timber.log.Timber;

/**
 * Login activity.
 *
 * Provide with facility to login either by using credentials or by simply scanning the QR.
 */

public class LoginActivity extends AppCompatActivity implements Callback<String> {

    @Bind(R.id.toolbar)
    Toolbar toolbar;

    @Bind(R.id.edit_username)
    EditText edit_username;

    @Bind(R.id.edit_password)
    EditText edit_password;

    @Bind(R.id.button_login)
    Button button_login;

    @Bind(R.id.button_login_qr)
    Button button_login_qr;

    String host;
    String port;
    public URL backendUrl;
    public String username;
    public String password;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        Bundle bundle = getIntent().getExtras();
        host = bundle.getString("host");
        port = bundle.getString("port");
        setUpBindings();
        setUpToolbar();
    }

    @OnClick(R.id.button_login_qr)
    public void login_qr() {
        IntentIntegrator integrator = new IntentIntegrator(LoginActivity.this);
        integrator.initiateScan();
    }

    private void setUpBindings() {
        ButterKnife.bind(this);
    }

    private void setUpToolbar() {
        setSupportActionBar(toolbar);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        if (scanResult != null && scanResult.getContents() != null) {
            BackendClient.of(this).configureAuthorization();
            String authorization = scanResult.getContents();
            String[] authArray = authorization.split(",");
            if (authArray.length >= 2) {
                BackendClient.of(this).saveCred(authArray[0], authArray[1]);
                setUpBackendCommunication(getMockPersona());
                setUpPersona();
            }
        }
    }

    private String getHost() {
        return host.trim();
    }

    private boolean isPortAvailable() {
        return !getPort().isEmpty();
    }

    private String getPort() {
        return port.trim();
    }

    private boolean isPortCorrect() {
        try {
            return Ports.isCorrect(getPortNumber());
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private int getPortNumber() {
        return Integer.valueOf(getPort());
    }

    @OnClick(R.id.button_login)
    public void login() {

        try {
            if (!isPortAvailable()) {

                backendUrl = Urls.getUrl(getHost());
            } else {
                backendUrl = Urls.getUrl(getHost(), getPortNumber());
            }
            username = edit_username.getText().toString();
            password = edit_password.getText().toString();
            BackendClient.of(this).configureAuthorization();
            BackendClient.of(this).deauthorize();
            BackendClient.of(this).authorize(this, this);
        } catch (RuntimeException e) {
            Timber.d(e, "Authorization failed.");

            showError(R.string.error_authorization_host_port);
        }
    }

    private void showError(@StringRes int errorMessage) {
        Snackbar.make(findViewById(android.R.id.content), errorMessage, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onFailure(Exception e) {
        Timber.d(e, "Authorization failed.");

        showError(R.string.error_general);
    }

    @Override
    public void onSuccess(String authorization) {
        try {
            JSONObject auth = new JSONObject(authorization);
            BackendClient.of(this).saveCred(auth.getString("key"),auth.getString("secret"));
            setUpBackendCommunication(getMockPersona());

            setUpPersona();
        } catch (JSONException e) {
            e.printStackTrace();
        }


    }

    private void setUpBackendCommunication(Persona persona) {
        if (!isPortAvailable()) {
            BackendClient.of(this).configureCommunication(getHost(), persona);
        } else {
            BackendClient.of(this).configureCommunication(getHost(), getPortNumber(), persona);
        }
    }

    private Persona getMockPersona() {
        return new Persona("", "");
    }

    private void setUpPersona() {
        BackendClient.of(this).getPersona(new PersonasCallback());
    }

    private void setUpEnvironment(Persona persona) {
        BackendClient.of(this).getEnvironments(new EnvironmentsCallback(persona));
    }

    private void succeed(Persona persona, Environment environment) {
        saveBackendPreferences(persona, environment);
        Preferences.of(LoginActivity.this).exist().set("true");
        setResult(Activity.RESULT_OK);
        finish();
        startActivity(new Intent(getApplicationContext(), DrawerActivity.class));
    }


    private void saveBackendPreferences(Persona persona, Environment environment) {
        Preferences.of(this).host().set(getHost());
        if (isPortAvailable()) {
            Preferences.of(this).port().set(getPortNumber());
        }

        Preferences.of(this).personaId().set(persona.getId());
        Preferences.of(this).personaName().set(persona.getName());
        Preferences.of(this).environment().set(environment.getId());
    }

    private static final class PersonasCallback extends AbstractActivityCallback<List<Persona>> {
        @Override
        public void onSuccess(List<Persona> personas) {
            if (personas.isEmpty()) {
                onFailure(new RuntimeException("Personas list is empty, this should not happen."));
                return;
            }

            // Unfortunately AeroGear does not support single item fetching.
            Persona persona = personas.get(0);
            LoginActivity activity = (LoginActivity) getActivity();

            activity.setUpBackendCommunication(persona);
            activity.setUpEnvironment(persona);
        }

        @Override
        public void onFailure(Exception e) {
            Timber.d(e, "Personas fetching failed.");
        }
    }

    private static final class EnvironmentsCallback extends AbstractActivityCallback<List<Environment>> {
        private final Persona persona;

        public EnvironmentsCallback(@NonNull Persona persona) {
            this.persona = persona;
        }

        @Override
        public void onSuccess(List<Environment> environments) {
            if (environments.isEmpty()) {
                onFailure(new RuntimeException("Environments list is empty, this should not happen."));
                return;
            }

            // The first environment is picked and used everywhere, this should change in the future.
            Environment environment = environments.get(0);
            LoginActivity activity = (LoginActivity) getActivity();
            activity.succeed(persona, environment);
        }

        @Override
        public void onFailure(Exception e) {
            Timber.d(e, "Environments fetching failed.");

        }
    }


}
