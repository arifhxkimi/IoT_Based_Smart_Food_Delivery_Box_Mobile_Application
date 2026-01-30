package com.arif.smartfooddeliverybox;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.appbar.MaterialToolbar;

public class FaqActivity extends BaseInsetActivity {

    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Force Light Mode (same as your other pages)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_faq);

        applyStatusBarInset();

        initViews();
        setupToolbar();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            // ✅ SAME AS ABOUT PAGE BACK BUTTON
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("FAQ");
        }

        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }
}
