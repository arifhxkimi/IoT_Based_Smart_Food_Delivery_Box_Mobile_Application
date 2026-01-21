package com.arif.smartfooddeliverybox;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate; // Added for Light Mode

import com.google.android.material.appbar.MaterialToolbar;

public class AboutActivity extends BaseInsetActivity {

    private MaterialToolbar toolbar;
    private TextView tvVersion, tvDescription;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Force Light Mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        applyStatusBarInset();

        initViews();
        setupToolbar();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvVersion = findViewById(R.id.tvVersion);
        tvDescription = findViewById(R.id.tvDescription);

        // Set app version
        tvVersion.setText("Version 1.0.0 (FYP Build)");

        // You can set the description here, or keep the hardcoded text in XML
        // tvDescription.setText("Your project abstract goes here...");
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("About");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }
}