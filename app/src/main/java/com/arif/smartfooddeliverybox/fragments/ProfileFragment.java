package com.arif.smartfooddeliverybox.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.arif.smartfooddeliverybox.AboutActivity;
import com.arif.smartfooddeliverybox.ChangePasswordActivity;
import com.arif.smartfooddeliverybox.EditProfileActivity;
import com.arif.smartfooddeliverybox.LoginActivity;
import com.arif.smartfooddeliverybox.NotificationsActivity;
import com.arif.smartfooddeliverybox.R;
import com.arif.smartfooddeliverybox.models.User;
import com.arif.smartfooddeliverybox.utils.FirebaseHelper;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

public class ProfileFragment extends Fragment {

    private TextView tvUserName, tvUserEmail, tvUserPhone;
    private View btnEditProfile, btnChangePassword, btnLogout;
    private View btnNotifications, btnAbout;
    private ProgressBar progressBar;

    private FirebaseHelper firebaseHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        initViews(view);
        setupListeners();
        loadUserProfile();

        return view;
    }

    private void initViews(View view) {
        tvUserName = view.findViewById(R.id.tvUserName);
        tvUserEmail = view.findViewById(R.id.tvUserEmail);
        tvUserPhone = view.findViewById(R.id.tvUserPhone);
        btnEditProfile = view.findViewById(R.id.btnEditProfile);
        btnChangePassword = view.findViewById(R.id.btnChangePassword);
        btnLogout = view.findViewById(R.id.btnLogout);
        btnNotifications = view.findViewById(R.id.btnNotifications);
        btnAbout = view.findViewById(R.id.btnAbout);
        progressBar = view.findViewById(R.id.progressBar);

        firebaseHelper = FirebaseHelper.getInstance();

        // Hide text until data loads to prevent flashing placeholder
        tvUserName.setVisibility(View.INVISIBLE);
        tvUserEmail.setVisibility(View.INVISIBLE);
        tvUserPhone.setVisibility(View.INVISIBLE);
    }

    private void setupListeners() {
        btnEditProfile.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), EditProfileActivity.class);
            startActivity(intent);
        });

        btnChangePassword.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), ChangePasswordActivity.class);
            startActivity(intent);
        });

        btnNotifications.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), NotificationsActivity.class);
            startActivity(intent);
        });

        btnAbout.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), AboutActivity.class);
            startActivity(intent);
        });

        btnLogout.setOnClickListener(v -> showLogoutDialog());
    }

    private void loadUserProfile() {
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }

        String userId = firebaseHelper.getCurrentUserId();
        if (userId == null) {
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
            return;
        }

        firebaseHelper.getUserRef(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }

                if (snapshot.exists()) {
                    User user = snapshot.getValue(User.class);
                    if (user != null) {
                        updateUI(user);
                    } else {
                        // User data is null, create default profile
                        createDefaultProfile();
                    }
                } else {
                    // User profile doesn't exist, create it
                    createDefaultProfile();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Error loading profile", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void createDefaultProfile() {
        String userId = firebaseHelper.getCurrentUserId();
        if (userId == null) return;

        // Get email from Firebase Auth
        String email = firebaseHelper.getCurrentUser() != null ?
                firebaseHelper.getCurrentUser().getEmail() : "user@email.com";

        // Create default user profile
        User defaultUser = new User(userId, "User Name", email, "+60123456789");

        // Save to Firebase
        firebaseHelper.getUserRef(userId).setValue(defaultUser)
                .addOnSuccessListener(aVoid -> {
                    updateUI(defaultUser);
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Profile created", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Failed to create profile", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateUI(User user) {
        if (tvUserName != null) {
            tvUserName.setText(user.getName() != null ? user.getName() : "User");
            tvUserName.setVisibility(View.VISIBLE);
        }
        if (tvUserEmail != null) {
            tvUserEmail.setText(user.getEmail() != null ? user.getEmail() : "");
            tvUserEmail.setVisibility(View.VISIBLE);
        }
        if (tvUserPhone != null) {
            tvUserPhone.setText(user.getPhone() != null ? user.getPhone() : "");
            tvUserPhone.setVisibility(View.VISIBLE);
        }
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> logout())
                .setNegativeButton("No", null)
                .show();
    }

    private void logout() {
        firebaseHelper.getAuth().signOut();

        if (getActivity() != null) {
            // Navigate to login
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            getActivity().finish();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload profile when returning from edit screen
        loadUserProfile();
    }
}