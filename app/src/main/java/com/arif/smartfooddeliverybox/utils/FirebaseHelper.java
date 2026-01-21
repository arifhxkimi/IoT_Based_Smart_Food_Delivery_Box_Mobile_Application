package com.arif.smartfooddeliverybox.utils;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class FirebaseHelper {

    private static FirebaseHelper instance;
    private final DatabaseReference databaseReference;
    private final FirebaseAuth firebaseAuth;

    private FirebaseHelper() {
        databaseReference = FirebaseDatabase.getInstance().getReference();
        firebaseAuth = FirebaseAuth.getInstance();
    }

    public static synchronized FirebaseHelper getInstance() {
        if (instance == null) instance = new FirebaseHelper();
        return instance;
    }

    public DatabaseReference getDatabaseReference() {
        return databaseReference;
    }

    public FirebaseAuth getAuth() {
        return firebaseAuth;
    }

    public FirebaseUser getCurrentUser() {
        return firebaseAuth.getCurrentUser();
    }

    public boolean isUserLoggedIn() {
        return firebaseAuth.getCurrentUser() != null;
    }

    public String getCurrentUserId() {
        return firebaseAuth.getCurrentUser() != null
                ? firebaseAuth.getCurrentUser().getUid()
                : null;
    }

    // ✅ CURRENT PROJECT PATHS

    public DatabaseReference getUsersRef() {
        return databaseReference.child("users");
    }

    public DatabaseReference getUserRef(String userId) {
        return databaseReference.child("users").child(userId);
    }

    public DatabaseReference getBoxesRef() {
        return databaseReference.child("boxes");
    }

    public DatabaseReference getBoxRef(String boxId) {
        return databaseReference.child("boxes").child(boxId);
    }

    public DatabaseReference getHistoryRef(String userId) {
        return databaseReference.child("history").child(userId);
    }
}
