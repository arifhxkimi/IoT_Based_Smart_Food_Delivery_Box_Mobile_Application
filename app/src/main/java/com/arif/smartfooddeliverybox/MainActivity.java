package com.arif.smartfooddeliverybox;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.arif.smartfooddeliverybox.fragments.DashboardFragment;
import com.arif.smartfooddeliverybox.fragments.HistoryFragment;
import com.arif.smartfooddeliverybox.fragments.ProfileFragment;
import com.arif.smartfooddeliverybox.FoodReminderWorker;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);

        // ✅ IMPORTANT: Enable edge-to-edge handling (we will apply insets in fragments)
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.activity_main);

        scheduleFoodReminderWorker();

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;

            int itemId = item.getItemId();
            if (itemId == R.id.nav_dashboard) {
                selectedFragment = new DashboardFragment();
            } else if (itemId == R.id.nav_history) {
                selectedFragment = new HistoryFragment();
            } else if (itemId == R.id.nav_profile) {
                selectedFragment = new ProfileFragment();
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
            }
            return true;
        });

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new DashboardFragment())
                    .commit();
        }
    }

    private void scheduleFoodReminderWorker() {
        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(
                        FoodReminderWorker.class,
                        15, TimeUnit.MINUTES
                ).build();

        WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                        "food_reminder_worker",
                        ExistingPeriodicWorkPolicy.KEEP,
                        request
                );
    }
}
