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
package org.hawkular.client.android.activity;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.hawkular.client.android.R;
import org.hawkular.client.android.adapter.PersonasAdapter;
import org.hawkular.client.android.backend.BackendClient;
import org.hawkular.client.android.backend.model.Environment;
import org.hawkular.client.android.backend.model.Persona;
import org.hawkular.client.android.util.Fragments;
import org.hawkular.client.android.util.Intents;
import org.hawkular.client.android.util.Ports;
import org.hawkular.client.android.util.Preferences;
import org.hawkular.client.android.util.ViewTransformer;
import org.hawkular.client.android.util.Views;
import org.jboss.aerogear.android.core.Callback;
import org.jboss.aerogear.android.pipe.callback.AbstractActivityCallback;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnItemClick;
import icepick.Icepick;
import icepick.Icicle;
import timber.log.Timber;

public final class DrawerActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener,
    Callback<String> {
    @Bind(R.id.toolbar)
    Toolbar toolbar;

    @Bind(R.id.drawer)
    DrawerLayout drawer;

    @Bind(R.id.navigation)
    NavigationView navigation;

    @Bind(R.id.text_host)
    TextView host;

    @Bind(R.id.text_persona)
    TextView persona;

    @Bind(R.id.list_personas)
    ListView personas;

    @Bind(R.id.layout_accounts)
    ViewGroup accounts;

    @Bind(R.id.image_header)
    ImageView motionIcon;

    @Icicle
    @IdRes
    int currentNavigationId;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_drawer);

        setUpState(state);

        setUpBindings();

        setUpToolbar();
        setUpNavigation();

        setUpBackendClient();
    }

    private void setUpState(Bundle state) {
        Icepick.restoreInstanceState(this, state);
    }

    private void setUpBindings() {
        ButterKnife.bind(this);
    }

    private void setUpToolbar() {
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_menu);
    }

    private void setUpNavigation() {
        navigation.setNavigationItemSelectedListener(this);
    }

    private void setUpBackendClient() {
        setUpBackendClient(getPersona());
    }

    private void setUpBackendClient(Persona persona) {
        String backendHost = Preferences.of(this).host().get();
        int backendPort = Preferences.of(this).port().get();

        if (backendHost.isEmpty() && !Ports.isCorrect(backendPort)) {
            startAuthorizationActivity();
            return;
        }

        if (!Ports.isCorrect(backendPort)) {
            BackendClient.of(this).configureAuthorization(backendHost);
            BackendClient.of(this).configureCommunication(backendHost, persona);
        } else {
            BackendClient.of(this).configureAuthorization(backendHost, backendPort);
            BackendClient.of(this).configureCommunication(backendHost, backendPort, persona);
        }

        BackendClient.of(this).authorize(this, this);
    }

    private Persona getPersona() {
        return new Persona(
            Preferences.of(this).personaId().get(),
            Preferences.of(this).personaName().get());
    }

    @Override
    public void onSuccess(String authorization) {
        setUpNavigationDefaults();
    }

    private void setUpNavigationDefaults() {
        if (currentNavigationId == 0) {
            showNavigation(R.id.menu_resources);

            showResourcesFragment();
        } else {
            showNavigation(currentNavigationId);
        }

        setUpNavigationHeader();
    }

    private void showNavigation(@IdRes int navigationId) {
        navigation.getMenu().findItem(navigationId).setChecked(true);
    }

    private void setUpNavigationHeader() {
        host.setText(Preferences.of(this).host().get());
        persona.setText(Preferences.of(this).personaName().get());

        setUpPersonas();
    }

    private void setUpPersonas() {
        BackendClient.of(this).getPersonas(new PersonasCallback());
    }

    private void setUpPersonas(List<Persona> personasList) {
        personas.setAdapter(new PersonasAdapter(this, personasList));

        setUpPersonasList();
        setUpPersonasAction();
    }

    private void setUpPersonasList() {
        ViewGroup.LayoutParams personasParams = personas.getLayoutParams();
        personasParams.height = Views.measureHeight(personas);

        personas.setLayoutParams(personasParams);
        personas.requestLayout();
    }

    private void setUpPersonasAction() {
        if (arePersonasAvailable()) {
            showPersonasAction();
        } else {
            hidePersonasAction();
        }
    }

    private static final class PersonasCallback extends AbstractActivityCallback<List<Persona>> {
        @Override
        public void onSuccess(List<Persona> personas) {
            getDrawerActivity().setUpPersonas(personas);
        }

        @Override
        public void onFailure(Exception e) {
            Timber.d(e, "Personas fetching failed.");

            getDrawerActivity().setUpPersonas(new ArrayList<Persona>());
        }

        private DrawerActivity getDrawerActivity() {
            return (DrawerActivity) getActivity();
        }
    }

    @OnItemClick(R.id.list_personas)
    public void setUpPersona(int personaPosition) {
        Persona persona = getPersonasAdapter().getItem(personaPosition);

        setUpBackendClient(persona);

        hidePersonas();
    }

    private PersonasAdapter getPersonasAdapter() {
        return (PersonasAdapter) personas.getAdapter();
    }

    @Override
    public void onFailure(Exception e) {
        startAuthorizationActivity();
    }

    private void startAuthorizationActivity() {
        Intent intent = Intents.Builder.of(this).buildAuthorizationIntent();
        startActivityForResult(intent, Intents.Requests.AUTHORIZATION);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent intent) {
        super.onActivityResult(request, result, intent);

        if (request != Intents.Requests.AUTHORIZATION) {
            return;
        }

        if (result == RESULT_OK) {
            setUpBackendClient();
        } else {
            finish();
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.menu_resources:
                showResourcesFragment();
                break;

            case R.id.menu_alerts:
                showAlertsFragment();
                break;

            case R.id.menu_settings:
                startSettingsActivity();
                break;

            case R.id.menu_feedback:
                startFeedbackActivity();
                break;

            default:
                break;
        }

        currentNavigationId = menuItem.getItemId();

        menuItem.setChecked(true);

        closeDrawers();

        return true;
    }

    private void showResourcesFragment() {
        Fragment fragment = Fragments.Builder.buildResourcesFragment(getEnvironment());

        Fragments.Operator.of(this).reset(R.id.layout_container, fragment);
    }

    private Environment getEnvironment() {
        return new Environment(Preferences.of(this).environment().get());
    }

    private void showAlertsFragment() {
        Fragment fragment = Fragments.Builder.buildAlertsFragment();

        Fragments.Operator.of(this).reset(R.id.layout_container, fragment);
    }

    private void startSettingsActivity() {
        Intent intent = Intents.Builder.of(this).buildSettingsIntent();
        startActivity(intent);
    }

    private void startFeedbackActivity() {
        Intent intent = Intents.Builder.of(this).buildFeedbackIntent();
        startActivity(intent);
    }

    private void closeDrawers() {
        drawer.closeDrawers();
    }

    @OnClick(R.id.layout_header)
    public void triggerPersonas() {
        if (!arePersonasAvailable()) {
            return;
        }

        if (Views.isVisible(accounts)) {
            hidePersonas();
        } else {
            showPersonas();
        }
    }

    private boolean arePersonasAvailable() {
        return (getPersonasAdapter() != null) && (getPersonasAdapter().getCount() > 1);
    }

    private void showPersonas() {
        ViewTransformer.of(accounts).expand();

        ViewTransformer.of(motionIcon).rotate();
    }

    private void hidePersonas() {
        ViewTransformer.of(accounts).collapse();

        ViewTransformer.of(motionIcon).rotate();
    }

    private void showPersonasAction() {
        ViewTransformer.of(motionIcon).show();
    }

    private void hidePersonasAction() {
        ViewTransformer.of(motionIcon).hide();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                openDrawer();
                return true;

            default:
                return super.onOptionsItemSelected(menuItem);
        }
    }

    private void openDrawer() {
        drawer.openDrawer(GravityCompat.START);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);

        tearDownState(state);
    }

    private void tearDownState(Bundle state) {
        Icepick.saveInstanceState(this, state);
    }
}
