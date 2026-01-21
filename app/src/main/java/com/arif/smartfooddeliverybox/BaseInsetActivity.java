package com.arif.smartfooddeliverybox;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Base class for all Activities that use custom headers (no Toolbar)
 * and need proper spacing under the status bar (notification bar).
 *
 * Usage:
 *   public class AboutActivity extends BaseInsetActivity { ... }
 */
public abstract class BaseInsetActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // ✅ Enable edge-to-edge for THIS activity
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        super.onCreate(savedInstanceState);
    }

    /**
     * Call this AFTER setContentView().
     * It will apply status bar height as paddingTop to the root content view.
     */
    protected void applyStatusBarInset() {
        final View content = findViewById(android.R.id.content);
        if (content == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            int topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;

            // Apply top padding = status bar height (keeps your header visible)
            v.setPadding(
                    v.getPaddingLeft(),
                    topInset,
                    v.getPaddingRight(),
                    v.getPaddingBottom()
            );

            return insets;
        });

        // Ensure insets are requested
        ViewCompat.requestApplyInsets(content);
    }

    /**
     * Optional: If some screens also need to avoid navigation bar overlap
     * (rare for your layouts), you can use this instead.
     */
    protected void applyStatusAndNavBarInsets() {
        final View content = findViewById(android.R.id.content);
        if (content == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            var sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            v.setPadding(
                    v.getPaddingLeft(),
                    sys.top,
                    v.getPaddingRight(),
                    sys.bottom
            );

            return insets;
        });

        ViewCompat.requestApplyInsets(content);
    }
}
