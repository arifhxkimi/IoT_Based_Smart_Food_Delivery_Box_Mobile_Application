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
import androidx.appcompat.app.AppCompatActivity;

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

public class EditProfileActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private ImageView ivProfile, btnChangePhoto;
    private TextInputEditText etName, etEmail, etPhone;
    private MaterialButton btnSave, btnCancel;
    private ProgressBar progressBar;

    private FirebaseHelper firebaseHelper;
    private String userId;
    private String encodedImage = ""; // Stores the Base64 image string

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

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
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        btnSave = findViewById(R.id.btnSave);
        btnCancel = findViewById(R.id.btnCancel);
        progressBar = findViewById(R.id.progressBar);

        // Email is usually fixed in Firebase Auth
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
        // 1. Click camera icon to pick image
        btnChangePhoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickImageLauncher.launch(intent);
        });

        // 2. Click image to pick image (UX improvement)
        ivProfile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickImageLauncher.launch(intent);
        });

        btnSave.setOnClickListener(v -> saveProfile());
        btnCancel.setOnClickListener(v -> finish());
    }

    // --- IMAGE HANDLING START ---

    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    try {
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                        ivProfile.setImageBitmap(bitmap); // Show selected image immediately
                        encodedImage = encodeImage(bitmap); // Convert to String for Firebase
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    private String encodeImage(Bitmap bitmap) {
        // Resize image to avoid "String too long" errors in Firebase
        int previewWidth = 300;
        int previewHeight = bitmap.getHeight() * previewWidth / bitmap.getWidth();
        Bitmap previewBitmap = Bitmap.createScaledBitmap(bitmap, previewWidth, previewHeight, false);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        // Compress quality 70 is a good balance
        previewBitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream);
        byte[] bytes = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }

    // --- IMAGE HANDLING END ---

    private void loadUserProfile() {
        progressBar.setVisibility(View.VISIBLE);
        btnSave.setEnabled(false);

        firebaseHelper.getUserRef(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                progressBar.setVisibility(View.GONE);
                btnSave.setEnabled(true);

                if (snapshot.exists()) {
                    User user = snapshot.getValue(User.class);
                    if (user != null) {
                        etName.setText(user.getName());
                        etEmail.setText(user.getEmail());
                        etPhone.setText(user.getPhone());

                        // Load Profile Image
                        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
                            try {
                                byte[] bytes = Base64.decode(user.getProfileImage(), Base64.DEFAULT);
                                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                ivProfile.setImageBitmap(bitmap);
                                encodedImage = user.getProfileImage(); // Keep existing image logic
                            } catch (Exception e) {
                                // Invalid image data, ignore
                            }
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                btnSave.setEnabled(true);
                Toast.makeText(EditProfileActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveProfile() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();

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

        // Update fields individually to avoid overwriting other data
        firebaseHelper.getUserRef(userId).child("name").setValue(name);
        firebaseHelper.getUserRef(userId).child("phone").setValue(phone);

        // Only update image if changed or exists
        if (!encodedImage.isEmpty()) {
            firebaseHelper.getUserRef(userId).child("profileImage").setValue(encodedImage);
        }

        // Add a small delay for better UX or wait for callbacks
        new android.os.Handler().postDelayed(() -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(EditProfileActivity.this, "Profile Updated!", Toast.LENGTH_SHORT).show();
            finish();
        }, 1000);
    }
}