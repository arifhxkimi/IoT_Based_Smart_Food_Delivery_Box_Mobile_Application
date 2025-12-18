package com.arif.smartfooddeliverybox;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.appbar.MaterialToolbar;

public class AboutActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextView tvVersion, tvDescription;
    private CardView cardPrivacy, cardTerms, cardContact, cardRate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        initViews();
        setupToolbar();
        setupListeners();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvVersion = findViewById(R.id.tvVersion);
        tvDescription = findViewById(R.id.tvDescription);
        cardPrivacy = findViewById(R.id.cardPrivacy);
        cardTerms = findViewById(R.id.cardTerms);
        cardContact = findViewById(R.id.cardContact);
        cardRate = findViewById(R.id.cardRate);

        // Set app version
        tvVersion.setText("Version 1.0.0");

        // Set description
        tvDescription.setText("Smart Food Delivery Box is an IoT-based solution that provides " +
                "secure and hygienic food delivery storage. The system automatically manages " +
                "box allocation, unlocking, and locking using sensors and Firebase integration.");
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("About");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupListeners() {
        cardPrivacy.setOnClickListener(v -> {
            // Open privacy policy (you can add a web link or dialog)
            openUrl("https://your-website.com/privacy");
        });

        cardTerms.setOnClickListener(v -> {
            // Open terms and conditions
            openUrl("https://your-website.com/terms");
        });

        cardContact.setOnClickListener(v -> {
            // Open email app
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:support@smartdeliverybox.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Smart Delivery Box - Support");
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            }
        });

        cardRate.setOnClickListener(v -> {
            // Open Play Store (replace with your app package name)
            openUrl("https://play.google.com/store/apps/details?id=com.arif.smartfooddeliverybox");
        });
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }
}