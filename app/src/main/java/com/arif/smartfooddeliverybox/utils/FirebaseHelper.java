package com.arif.smartfooddeliverybox.utils;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class FirebaseHelper {
    private static FirebaseHelper instance;
    private final DatabaseReference databaseReference;
    private final FirebaseAuth firebaseAuth;

    private FirebaseHelper() {
        databaseReference = FirebaseDatabase.getInstance().getReference();
        firebaseAuth = FirebaseAuth.getInstance();
    }

    public static synchronized FirebaseHelper getInstance() {
        if (instance == null) {
            instance = new FirebaseHelper();
        }
        return instance;
    }

    public DatabaseReference getDeliveryBoxRef(String deviceId) {
        return databaseReference.child("deliveryBox").child(deviceId);
    }

    public DatabaseReference getUserRef(String userId) {
        return databaseReference.child("users").child(userId);
    }

    public DatabaseReference getDeliveryHistoryRef(String userId) {
        return databaseReference.child("deliveryHistory").child(userId);
    }

    public void unlockBox(String deviceId) {
        getDeliveryBoxRef(deviceId).child("isLocked").setValue(false);
        getDeliveryBoxRef(deviceId).child("lastUpdated").setValue(System.currentTimeMillis());
    }

    public void lockBox(String deviceId) {
        getDeliveryBoxRef(deviceId).child("isLocked").setValue(true);
        getDeliveryBoxRef(deviceId).child("lastUpdated").setValue(System.currentTimeMillis());
    }

    public void listenToBoxStatus(String deviceId, ValueEventListener listener) {
        getDeliveryBoxRef(deviceId).addValueEventListener(listener);
    }

    public String getCurrentUserId() {
        return firebaseAuth.getCurrentUser() != null ?
                firebaseAuth.getCurrentUser().getUid() : null;
    }

    public FirebaseAuth getAuth() {
        return firebaseAuth;
    }

    public DatabaseReference getDatabaseReference() {
        return databaseReference;
    }

    // ADD THESE NEW METHODS NEEDED BY YOUR ACTIVITIES

    public FirebaseUser getCurrentUser() {
        return firebaseAuth.getCurrentUser();
    }

    public boolean isUserLoggedIn() {
        return firebaseAuth.getCurrentUser() != null;
    }

    public DatabaseReference getBoxesRef() {
        return databaseReference.child("boxes");
    }

    public DatabaseReference getBoxRef(String boxId) {
        return databaseReference.child("boxes").child(boxId);
    }

    public DatabaseReference getDeliveriesRef() {
        return databaseReference.child("deliveries");
    }

    public DatabaseReference getHistoryRef(String userId) {
        return databaseReference.child("history").child(userId);
    }
}