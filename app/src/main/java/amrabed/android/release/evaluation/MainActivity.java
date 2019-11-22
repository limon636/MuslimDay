package amrabed.android.release.evaluation;

import android.content.Intent;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.firebase.ui.auth.AuthUI;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Collections;
import java.util.List;

import amrabed.android.release.evaluation.db.DatabaseUpdater;
import amrabed.android.release.evaluation.edit.EditSection;
import amrabed.android.release.evaluation.locale.LocaleManager;
import amrabed.android.release.evaluation.sync.SyncActivity;


/**
 * Main Activity
 */
public class MainActivity extends SyncActivity {
    public static final int SIGN_IN_REQUEST = 100;
    private static final int EDIT_REQUEST = 10;
    private FirebaseUser user;
    private NavigationDrawer drawer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme_NoActionBar);
        super.onCreate(savedInstanceState);
        LocaleManager.setLocale(this);
        setContentView(R.layout.main_activity);
        startService(new Intent(getApplicationContext(), DatabaseUpdater.class));

        user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivityForResult(createSignInIntent(), SIGN_IN_REQUEST);
        } else {
            showWelcomeMessage();
            updateProfilePicture();
        }

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawer = new NavigationDrawer(this).create(savedInstanceState, toolbar);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState, @NonNull PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        drawer.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocaleManager.setLocale(this);
    }

    @Override
    public void onBackPressed() {
        if (drawer.isOpen()) {
            drawer.close();
        } else {
            drawer.onBackStackChanged();
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SIGN_IN_REQUEST) {
            if (resultCode == RESULT_OK) { // Successfully signed in
                user = FirebaseAuth.getInstance().getCurrentUser();
                showWelcomeMessage();
            } else {
                new AlertDialog.Builder(this).setTitle(R.string.no_sign_in)
                        .setMessage(R.string.no_sig_in_confirm)
                        .setPositiveButton(R.string.sign_in, (dialog, i) -> startActivityForResult(createSignInIntent(), SIGN_IN_REQUEST))
                        .setNegativeButton(R.string.cancel, ((dialog, i) -> dialog.dismiss()))
                        .create().show();
            }
        } else if (requestCode == EDIT_REQUEST) {
            if (resultCode == RESULT_OK) {
                recreate();
            }
        }
    }

    private void showWelcomeMessage() {
        if (user != null) {
            final String name = user.getDisplayName();
            final String text = (name != null) ?
                    getString(R.string.welcome) + " " + name.split(" ")[0] :
                    getString(R.string.signed_in);
            if (getCurrentFocus() != null) {
                Snackbar.make(getCurrentFocus(), text, Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    private void updateProfilePicture() {
        Glide.with(this).load(user.getPhotoUrl()).into((ImageView) findViewById(R.id.user));
    }

    public void startEditorActivity() {
        startActivityForResult(new Intent(this, EditSection.class), EDIT_REQUEST);
    }

    private static Intent createSignInIntent() {
        final List<AuthUI.IdpConfig> providers = Collections.singletonList(
                new AuthUI.IdpConfig.GoogleBuilder().build());
//                new AuthUI.IdpConfig.AnonymousBuilder().build());

        return AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(providers)
                .setLogo(R.drawable.logo)
                .setTheme(R.style.AppTheme_FullScreen)
                .build();
    }

    public void signOut() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(this, R.string.no_sign_in, Toast.LENGTH_SHORT).show();
        } else {
            new AlertDialog.Builder(this).setTitle(R.string.sign_out)
                    .setMessage(R.string.sign_out_confirm)
                    .setPositiveButton(R.string.sign_out, (dialog, i) -> {
                        AuthUI.getInstance().signOut(MainActivity.this)
                                .addOnCompleteListener(task -> Toast.makeText(getApplicationContext(),
                                        R.string.signed_out, Toast.LENGTH_SHORT).show());
                        Glide.with(MainActivity.this).clear((ImageView) findViewById(R.id.user));

                    })
                    .setNegativeButton(R.string.cancel, ((dialog, i) -> dialog.dismiss()))
                    .create().show();
        }
    }
}