package com.arif.smartfooddeliverybox;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.CompoundButton;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class NotificationsActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private SwitchMaterial switchDeliveryNotif, switchRetrievalNotif, switchSystemNotif;

    private SharedPreferences preferences;
    private static final String PREFS_NAME = "NotificationPrefs";
    private static final String KEY_DELIVERY = "delivery_notifications";
    private static final String KEY_RETRIEVAL = "retrieval_notifications";
    private static final String KEY_SYSTEM = "system_notifications";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        initViews();
        setupToolbar();
        loadPreferences();
        setupListeners();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        switchDeliveryNotif = findViewById(R.id.switchDeliveryNotif);
        switchRetrievalNotif = findViewById(R.id.switchRetrievalNotif);
        switchSystemNotif = findViewById(R.id.switchSystemNotif);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Notifications");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void loadPreferences() {
        switchDeliveryNotif.setChecked(preferences.getBoolean(KEY_DELIVERY, true));
        switchRetrievalNotif.setChecked(preferences.getBoolean(KEY_RETRIEVAL, true));
        switchSystemNotif.setChecked(preferences.getBoolean(KEY_SYSTEM, true));
    }

    private void setupListeners() {
        switchDeliveryNotif.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(KEY_DELIVERY, isChecked).apply();
        });

        switchRetrievalNotif.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(KEY_RETRIEVAL, isChecked).apply();
        });

        switchSystemNotif.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(KEY_SYSTEM, isChecked).apply();
        });
    }
}