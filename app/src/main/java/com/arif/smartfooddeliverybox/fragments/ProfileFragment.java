package com.arif.smartfooddeliverybox.fragments;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.arif.smartfooddeliverybox.AboutActivity;
import com.arif.smartfooddeliverybox.ChangePasswordActivity;
import com.arif.smartfooddeliverybox.EditProfileActivity;
import com.arif.smartfooddeliverybox.FaqActivity;   // ✅ add
import com.arif.smartfooddeliverybox.LoginActivity;
import com.arif.smartfooddeliverybox.NotificationsActivity;
import com.arif.smartfooddeliverybox.R;
import com.arif.smartfooddeliverybox.models.User;
import com.arif.smartfooddeliverybox.utils.FirebaseHelper;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

public class ProfileFragment extends BaseInsetFragment {

    private ImageView ivProfile;
    private TextView tvUserName, tvUserEmail, tvUserPhone;
    private View btnEditProfile, btnChangePassword, btnLogout;
    private View btnNotifications, btnAbout, btnFaq; // ✅ add
    private ProgressBar progressBar;

    private FirebaseHelper firebaseHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        applyStatusBarInset(view);

        initViews(view);
        setupListeners();
        loadUserProfile();

        return view;
    }

    private void initViews(View view) {
        ivProfile = view.findViewById(R.id.ivProfile);
        tvUserName = view.findViewById(R.id.tvUserName);
        tvUserEmail = view.findViewById(R.id.tvUserEmail);
        tvUserPhone = view.findViewById(R.id.tvUserPhone);

        btnEditProfile = view.findViewById(R.id.btnEditProfile);
        btnChangePassword = view.findViewById(R.id.btnChangePassword);
        btnLogout = view.findViewById(R.id.btnLogout);
        btnNotifications = view.findViewById(R.id.btnNotifications);
        btnAbout = view.findViewById(R.id.btnAbout);

        // ✅ new FAQ row
        btnFaq = view.findViewById(R.id.btnFaq);

        progressBar = view.findViewById(R.id.progressBar);

        firebaseHelper = FirebaseHelper.getInstance();

        tvUserName.setVisibility(View.INVISIBLE);
        tvUserEmail.setVisibility(View.INVISIBLE);
        tvUserPhone.setVisibility(View.INVISIBLE);

        ivProfile.setImageTintList(null);
        ivProfile.clearColorFilter();
    }

    private void setupListeners() {
        btnEditProfile.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), EditProfileActivity.class)));

        btnChangePassword.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), ChangePasswordActivity.class)));

        btnNotifications.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), NotificationsActivity.class)));

        btnAbout.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), AboutActivity.class)));

        // ✅ open FAQ page
        if (btnFaq != null) {
            btnFaq.setOnClickListener(v ->
                    startActivity(new Intent(getActivity(), FaqActivity.class)));
        }

        btnLogout.setOnClickListener(v -> showLogoutDialog());
    }

    private void loadUserProfile() {
        progressBar.setVisibility(View.VISIBLE);

        String userId = firebaseHelper.getCurrentUserId();
        if (userId == null) {
            progressBar.setVisibility(View.GONE);
            return;
        }

        firebaseHelper.getUserRef(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {

                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!isAdded() || getContext() == null) return;

                        progressBar.setVisibility(View.GONE);

                        if (snapshot.exists()) {
                            User user = snapshot.getValue(User.class);
                            if (user != null) updateUI(user);
                            else createDefaultProfile();
                        } else {
                            createDefaultProfile();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (!isAdded() || getContext() == null) return;
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(getContext(), "Failed to load profile", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateUI(User user) {
        tvUserName.setText(user.getName() != null ? user.getName() : "User");
        tvUserEmail.setText(user.getEmail() != null ? user.getEmail() : "");
        tvUserPhone.setText(user.getPhone() != null ? user.getPhone() : "");

        tvUserName.setVisibility(View.VISIBLE);
        tvUserEmail.setVisibility(View.VISIBLE);
        tvUserPhone.setVisibility(View.VISIBLE);

        String img = user.getProfileImage();
        boolean hasPhoto = img != null && !img.trim().isEmpty();

        if (hasPhoto) {
            try {
                byte[] bytes = Base64.decode(img, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                if (bitmap != null) {
                    showBitmapAvatar(bitmap);
                    return;
                }
            } catch (Exception ignored) {}
        }

        showDefaultAvatar();
    }

    private void showBitmapAvatar(Bitmap bitmap) {
        ivProfile.setPadding(0, 0, 0, 0);
        ivProfile.clearColorFilter();
        ivProfile.setImageTintList(null);
        ivProfile.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivProfile.setImageBitmap(bitmap);
    }

    private void showDefaultAvatar() {
        ivProfile.setImageResource(R.drawable.ic_user);
        ivProfile.setScaleType(ImageView.ScaleType.FIT_CENTER);

        int pad = dpToPx(18);
        ivProfile.setPadding(pad, pad, pad, pad);

        ivProfile.setColorFilter(ContextCompat.getColor(requireContext(), R.color.primary));
    }

    private int dpToPx(int dp) {
        return (int) (dp * requireContext().getResources().getDisplayMetrics().density);
    }

    private void createDefaultProfile() {
        String userId = firebaseHelper.getCurrentUserId();
        if (userId == null) return;

        String email = firebaseHelper.getCurrentUser() != null
                ? firebaseHelper.getCurrentUser().getEmail()
                : "user@email.com";

        User user = new User(userId, "User Name", email, "+60123456789");

        firebaseHelper.getUserRef(userId).setValue(user)
                .addOnSuccessListener(v -> updateUI(user))
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Failed to create profile", Toast.LENGTH_SHORT).show());
    }

    private void showLogoutDialog() {
        if (getContext() == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure?")
                .setPositiveButton("Yes", (d, w) -> logout())
                .setNegativeButton("No", null)
                .show();
    }

    private void logout() {
        if (!isAdded()) return;

        firebaseHelper.getAuth().signOut();

        Intent intent = new Intent(getActivity(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);

        if (getActivity() != null) getActivity().finish();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadUserProfile();
    }
}
