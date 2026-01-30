package com.arif.smartfooddeliverybox;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.arif.smartfooddeliverybox.models.User;
import com.arif.smartfooddeliverybox.utils.FirebaseHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends BaseInsetActivity {

    private MaterialToolbar toolbar;
    private ImageView ivProfile, btnChangePhoto;
    private TextInputEditText etName, etEmail, etPhone;
    private MaterialButton btnSave, btnCancel, btnRemovePhoto;
    private ProgressBar progressBar;

    private FirebaseHelper firebaseHelper;
    private String userId;

    private boolean removePhoto = false;
    private String encodedImage = "";

    // ✅ Camera launcher (simple preview bitmap)
    private final ActivityResultLauncher<Void> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicturePreview(),
                    bitmap -> {
                        if (bitmap == null) {
                            Toast.makeText(this, "Camera cancelled", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        removePhoto = false;
                        showBitmapAvatar(bitmap);
                        encodedImage = encodeImage(bitmap);
                        btnRemovePhoto.setVisibility(View.VISIBLE);
                    });

    // ✅ Gallery launcher (GetContent is simpler than ACTION_PICK intent)
    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri == null) return;

                        try {
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);

                            removePhoto = false;
                            showBitmapAvatar(bitmap);
                            encodedImage = encodeImage(bitmap);
                            btnRemovePhoto.setVisibility(View.VISIBLE);

                        } catch (IOException e) {
                            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        applyStatusBarInset();

        firebaseHelper = FirebaseHelper.getInstance();
        userId = firebaseHelper.getCurrentUserId();

        if (userId == null) {
            Toast.makeText(this, "Error: Not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupToolbar();
        setupListeners();
        loadUserProfile();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        ivProfile = findViewById(R.id.ivProfile);
        btnChangePhoto = findViewById(R.id.btnChangePhoto);
        btnRemovePhoto = findViewById(R.id.btnRemovePhoto);

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);

        btnSave = findViewById(R.id.btnSave);
        btnCancel = findViewById(R.id.btnCancel);

        progressBar = findViewById(R.id.progressBar);

        etEmail.setEnabled(false);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Edit Profile");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupListeners() {
        // ✅ Now open dialog instead of gallery directly
        btnChangePhoto.setOnClickListener(v -> showPhotoOptions());
        ivProfile.setOnClickListener(v -> showPhotoOptions());

        btnRemovePhoto.setOnClickListener(v -> confirmRemovePhoto());

        btnSave.setOnClickListener(v -> saveProfile());
        btnCancel.setOnClickListener(v -> finish());
    }

    // ✅ Choose Camera or Gallery
    private void showPhotoOptions() {
        String[] options = {"Take Photo", "Choose from Gallery"};

        new AlertDialog.Builder(this)
                .setTitle("Profile Photo")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // Camera
                        cameraLauncher.launch(null);
                    } else {
                        // Gallery
                        galleryLauncher.launch("image/*");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String encodeImage(Bitmap bitmap) {
        int previewWidth = 500;
        int previewHeight = bitmap.getHeight() * previewWidth / Math.max(1, bitmap.getWidth());
        Bitmap previewBitmap = Bitmap.createScaledBitmap(bitmap, previewWidth, previewHeight, true);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        previewBitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
    }

    private void loadUserProfile() {
        progressBar.setVisibility(View.VISIBLE);
        btnSave.setEnabled(false);

        firebaseHelper.getUserRef(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                progressBar.setVisibility(View.GONE);
                btnSave.setEnabled(true);

                if (!snapshot.exists()) {
                    showDefaultAvatar();
                    btnRemovePhoto.setVisibility(View.GONE);
                    return;
                }

                User user = snapshot.getValue(User.class);
                if (user == null) {
                    showDefaultAvatar();
                    btnRemovePhoto.setVisibility(View.GONE);
                    return;
                }

                etName.setText(user.getName());
                etEmail.setText(user.getEmail());
                etPhone.setText(user.getPhone());

                String img = user.getProfileImage();
                boolean hasPhoto = img != null && !img.trim().isEmpty();

                if (hasPhoto) {
                    try {
                        byte[] bytes = Base64.decode(img, Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                        if (bitmap != null) {
                            showBitmapAvatar(bitmap);
                            encodedImage = img;
                            btnRemovePhoto.setVisibility(View.VISIBLE);
                            return;
                        }
                    } catch (Exception ignored) {}
                }

                showDefaultAvatar();
                btnRemovePhoto.setVisibility(View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                btnSave.setEnabled(true);
                Toast.makeText(EditProfileActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
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

        ivProfile.setColorFilter(ContextCompat.getColor(this, R.color.primary));
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void confirmRemovePhoto() {
        new AlertDialog.Builder(this)
                .setTitle("Remove Profile Photo")
                .setMessage("Remove your profile photo?")
                .setPositiveButton("Remove", (d, w) -> {
                    removePhoto = true;
                    encodedImage = "";
                    showDefaultAvatar();
                    btnRemovePhoto.setVisibility(View.GONE);
                    Toast.makeText(this, "Photo removed (tap Save to apply)", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveProfile() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";

        if (name.isEmpty()) {
            etName.setError("Name required");
            return;
        }
        if (phone.isEmpty()) {
            etPhone.setError("Phone required");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnSave.setEnabled(false);

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("phone", phone);

        if (removePhoto) {
            updates.put("profileImage", null);
        } else if (encodedImage != null && !encodedImage.isEmpty()) {
            updates.put("profileImage", encodedImage);
        }

        firebaseHelper.getUserRef(userId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(EditProfileActivity.this, "Profile Updated!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnSave.setEnabled(true);
                    Toast.makeText(EditProfileActivity.this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
