package com.estg.masters.pedwm.smarthome.activity;

import android.content.Intent;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.estg.masters.pedwm.smarthome.R;
import com.estg.masters.pedwm.smarthome.model.User;
import com.estg.masters.pedwm.smarthome.repository.UserRepository;
import com.estg.masters.pedwm.smarthome.ui.IntentNavigationUtils;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;

import static com.google.android.gms.auth.api.Auth.GOOGLE_SIGN_IN_API;

public class LoginActivity extends AppCompatActivity
        implements GoogleApiClient.OnConnectionFailedListener, View.OnClickListener {

    private static final int RC_SIGN_IN = 9001;
    private GoogleApiClient googleApiClient;
    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth mAuth;

    private SignInButton googleSignInButton;
    private Button normalSignInButton;

    private EditText emailInput;
    private EditText passwordInput;

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        redirectToMainIfLoggedIn();
        super.onCreate(savedInstanceState);
        initActivity();
    }

    private void initActivity() {
        setContentView(R.layout.activity_login);
        GoogleSignInOptions googleSignInOptions =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(getString(R.string.default_web_client_id))
                        .requestEmail()
                        .build();

        googleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, this)
                .addApi(GOOGLE_SIGN_IN_API, googleSignInOptions)
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, googleSignInOptions);

        googleSignInButton = findViewById(R.id.google_sign_in_button);
        normalSignInButton = findViewById(R.id.normal_login_button);

        emailInput = findViewById(R.id.emailLogin);
        passwordInput = findViewById(R.id.passwordLogin);

        googleSignInButton.setOnClickListener(this);
        normalSignInButton.setOnClickListener(this);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void redirectToMainIfLoggedIn() {
        mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() != null) goToActivityAndFinish(MainActivity.class);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.google_sign_in_button) {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        } else if (v.getId() == R.id.normal_login_button) {
            firebaseLogin();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void firebaseLogin() {
        String email = emailInput.getText().toString();
        String password = passwordInput.getText().toString();

        // try to login
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Log in success
                        Log.d("", "createUserWithEmail:success");
                        goToActivityAndFinish(MainActivity.class);
                    } else {
                        // If log in fails, try to sign in
                        firebaseSignIn(email, password);
                    }
                });
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void firebaseSignIn(String email, String password) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        getDisplayName(displayName -> {
                            UserProfileChangeRequest profileUpdates =
                                    new UserProfileChangeRequest.Builder()
                                            .setDisplayName(displayName).build();

                            Optional.ofNullable(mAuth.getCurrentUser())
                                    .ifPresent(firebaseUser ->
                                            firebaseUser.updateProfile(profileUpdates));

                            goToActivityAndFinish(MainActivity.class);
                        });
                    } else {
                        Log.w("", "createUserWithEmail:failure", task.getException());
                        Toast.makeText(LoginActivity.this, "Authentication failed.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void getDisplayName(Consumer<String> displayNameConsumer) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose your display name");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) ->
                displayNameConsumer.accept(input.getText().toString()));
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                handleSignInAccount(account);
                goToActivityAndFinish(MainActivity.class);
            } catch (ApiException e) {
                Log.w("Login", "Google sign in failed", e);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void handleSignInAccount(GoogleSignInAccount account) {
        firebaseAuthWithGoogle(account);
        UserRepository.getInstance().add(
                account.getId(),
                User.Builder.aUser()
                        .withId(account.getId())
                        .withName(account.getDisplayName())
                        .withTokens(new ArrayList<>())
                        .build()
        );
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        goToActivityAndFinish(MainActivity.class);
                    } else {
                        Log.d("Error", "Auth failed!");
                    }
                });
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void goToActivityAndFinish(Class activityToGo) {
        IntentNavigationUtils.goToActivityAndFinish(LoginActivity.this, activityToGo,
                Collections.emptyMap());
    }
}
