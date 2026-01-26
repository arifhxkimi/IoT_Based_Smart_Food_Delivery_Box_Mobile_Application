package com.arif.smartfooddeliverybox;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.arif.smartfooddeliverybox.fragments.DashboardFragment;
import com.arif.smartfooddeliverybox.fragments.HistoryFragment;
import com.arif.smartfooddeliverybox.fragments.ProfileFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String WORK_NAME = "food_reminder_worker";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        // ✅ Schedule only if logged in
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            scheduleFoodReminderWorker();

//            // 🚧 TEST ONLY (comment out after testing)
//            scheduleTestFoodReminder();

        } else {
            // optional: cancel it if somehow still exists
            WorkManager.getInstance(this).cancelUniqueWork(WORK_NAME);
        }

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
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(
                        FoodReminderWorker.class,
                        15, TimeUnit.MINUTES
                )
                        .setConstraints(constraints)
                        .build();

        WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                        WORK_NAME,
                        ExistingPeriodicWorkPolicy.KEEP,
                        request
                );
    }

    private void scheduleTestFoodReminder() {
        OneTimeWorkRequest testRequest =
                new OneTimeWorkRequest.Builder(FoodReminderWorker.class)
                        .setInitialDelay(30, TimeUnit.SECONDS)
                        .build();

        WorkManager.getInstance(this).enqueue(testRequest);
    }

}
