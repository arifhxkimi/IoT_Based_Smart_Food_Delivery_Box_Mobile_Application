package com.arif.smartfooddeliverybox;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword;
    private MaterialButton btnLogin;
    private TextView tvRegister, tvForgotPassword;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            navigateToMain();
            return;
        }

        initViews();
        setupListeners();
    }

    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> loginUser());

        tvRegister.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class))
        );

        tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
    }

    // ---------------- LOGIN ----------------

    private void loginUser() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            etEmail.requestFocus();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Please enter a valid email");
            etEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }

        showProgress(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    showProgress(false);

                    if (task.isSuccessful()) {
                        Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                        navigateToMain();
                    } else {
                        String errorMessage = "Login failed";

                        if (task.getException() != null && task.getException().getMessage() != null) {
                            String error = task.getException().getMessage().toLowerCase();

                            if (error.contains("no user record") || error.contains("user not found")) {
                                errorMessage = "No account found. Please register first.";
                            } else if (error.contains("password is invalid") || error.contains("wrong password")) {
                                errorMessage = "Incorrect password. Please try again.";
                            } else if (error.contains("badly formatted") || error.contains("invalid email")) {
                                errorMessage = "Invalid email format.";
                            } else if (error.contains("network error")) {
                                errorMessage = "Network error. Check your internet connection.";
                            } else if (error.contains("too many requests")) {
                                errorMessage = "Too many login attempts. Please try again later.";
                            } else if (error.contains("disabled")) {
                                errorMessage = "This account has been disabled.";
                            } else {
                                errorMessage = task.getException().getMessage();
                            }
                        }

                        Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ---------------- FORGOT PASSWORD ----------------

    private void showForgotPasswordDialog() {
        // Prefill with whatever user already typed
        final TextInputEditText input = new TextInputEditText(this);
        input.setHint("Enter your email");
        input.setText(etEmail.getText() != null ? etEmail.getText().toString().trim() : "");

        new AlertDialog.Builder(this)
                .setTitle("Reset Password")
                .setMessage("We will send a password reset link to your email.")
                .setView(input)
                .setPositiveButton("Send", (dialog, which) -> {
                    String email = input.getText() != null ? input.getText().toString().trim() : "";

                    if (TextUtils.isEmpty(email)) {
                        Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    sendPasswordReset(email);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void sendPasswordReset(@NonNull String email) {
        showProgress(true);

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    showProgress(false);

                    if (task.isSuccessful()) {
                        Toast.makeText(
                                LoginActivity.this,
                                "Reset link sent! Check your email inbox/spam.",
                                Toast.LENGTH_LONG
                        ).show();
                    } else {
                        String msg = "Failed to send reset email.";
                        if (task.getException() != null && task.getException().getMessage() != null) {
                            msg = task.getException().getMessage();
                        }
                        Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ---------------- NAV + UI ----------------

    private void navigateToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!show);
        etEmail.setEnabled(!show);
        etPassword.setEnabled(!show);
        tvRegister.setEnabled(!show);
        tvForgotPassword.setEnabled(!show);
    }
}
