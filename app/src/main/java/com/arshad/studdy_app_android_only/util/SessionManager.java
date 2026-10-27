package com.arshad.studdy_app_android_only.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Manages all session state for the Studdy app.
 *
 * Teacher session:
 *   Stored in EncryptedSharedPreferences (AES-256-GCM encrypted at rest).
 *   Holds: access_token, refresh_token, user_id, email, display_name.
 *
 * Student session:
 *   Enrollment number + display name in plain SharedPreferences
 *   (enrollment number is not a secret).
 *   Access/refresh tokens in EncryptedSharedPreferences, same as teacher
 *   tokens — tokens must never live in plain prefs.
 *   Holds: enrollment_number, student_name (plain);
 *          student_access_token, student_refresh_token (encrypted).
 *
 * If encrypted storage cannot be created, token operations fail closed:
 * no token is written anywhere and token getters return null, forcing a
 * fresh login instead of silently downgrading to plaintext storage.
 */
public class SessionManager {

    private static final String TAG = "SessionManager";

    // ── Encrypted prefs for teacher + student tokens ─────────────────────
    private static final String ENCRYPTED_PREFS_FILE = "studdy_teacher_session";
    private static final String KEY_ACCESS_TOKEN   = "access_token";
    private static final String KEY_REFRESH_TOKEN  = "refresh_token";
    private static final String KEY_TEACHER_ID     = "teacher_id";
    private static final String KEY_TEACHER_EMAIL  = "teacher_email";
    private static final String KEY_TEACHER_NAME   = "teacher_name";
    private static final String KEY_STUDENT_ACCESS_TOKEN = "student_access_token";
    private static final String KEY_STUDENT_REFRESH_TOKEN = "student_refresh_token";

    // ── Plain prefs for student enrollment (not secrets) ────────────────
    private static final String PLAIN_PREFS_FILE   = "studdy_student_session";
    private static final String KEY_ENROLLMENT     = "enrollment_number";
    private static final String KEY_STUDENT_NAME   = "student_name";

    private static SessionManager instance;

    private SharedPreferences encryptedPrefs;
    private SharedPreferences plainPrefs;

    private SessionManager() { /* use getInstance(context) */ }

    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager();
        }
        instance.init(context.getApplicationContext());
        return instance;
    }

    public static synchronized SessionManager getInstance() {
        return instance;
    }

    private void init(Context context) {
        if (encryptedPrefs == null) {
            try {
                MasterKey masterKey = new MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();

                encryptedPrefs = EncryptedSharedPreferences.create(
                        context,
                        ENCRYPTED_PREFS_FILE,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
            } catch (Exception e) {
                Log.e(TAG, "Failed to create EncryptedSharedPreferences", e);
                // Fail closed: leave encryptedPrefs null. Token getters
                // return null and token writes are skipped, so no token
                // is ever written to plaintext storage. Never fall back
                // to a plaintext prefs file.
                encryptedPrefs = null;
            }
        }

        if (plainPrefs == null) {
            plainPrefs = context.getSharedPreferences(PLAIN_PREFS_FILE, Context.MODE_PRIVATE);
        }

        // One-time migration (Stage 1 fix): student tokens written to plain
        // prefs by older builds move into encrypted prefs, then are removed
        // from plain prefs. Idempotent — after the first move there is
        // nothing left to migrate.
        if (encryptedPrefs != null) {
            String oldAccess = plainPrefs.getString(KEY_STUDENT_ACCESS_TOKEN, null);
            String oldRefresh = plainPrefs.getString(KEY_STUDENT_REFRESH_TOKEN, null);
            if (oldAccess != null || oldRefresh != null) {
                SharedPreferences.Editor ed = encryptedPrefs.edit();
                if (oldAccess != null) {
                    ed.putString(KEY_STUDENT_ACCESS_TOKEN, oldAccess);
                }
                if (oldRefresh != null) {
                    ed.putString(KEY_STUDENT_REFRESH_TOKEN, oldRefresh);
                }
                ed.apply();
                plainPrefs.edit()
                        .remove(KEY_STUDENT_ACCESS_TOKEN)
                        .remove(KEY_STUDENT_REFRESH_TOKEN)
                        .apply();
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Teacher session
    // ══════════════════════════════════════════════════════════════════════

    public void saveTeacherSession(String accessToken, String refreshToken,
                                   String userId, String email, String name) {
        if (encryptedPrefs == null) {
            Log.e(TAG, "Cannot save teacher session: encrypted storage unavailable");
            return;
        }
        encryptedPrefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putString(KEY_TEACHER_ID, userId)
                .putString(KEY_TEACHER_EMAIL, email)
                .putString(KEY_TEACHER_NAME, name != null ? name : "")
                .apply();
    }

    public boolean hasTeacherSession() {
        if (encryptedPrefs == null) {
            return false;
        }
        String token = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null);
        return token != null && !token.isEmpty();
    }

    public String getAccessToken() {
        if (encryptedPrefs == null) {
            return null;
        }
        return encryptedPrefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getRefreshToken() {
        if (encryptedPrefs == null) {
            return null;
        }
        return encryptedPrefs.getString(KEY_REFRESH_TOKEN, null);
    }

    public String getTeacherId() {
        if (encryptedPrefs == null) {
            return null;
        }
        return encryptedPrefs.getString(KEY_TEACHER_ID, null);
    }

    public String getTeacherEmail() {
        if (encryptedPrefs == null) {
            return null;
        }
        return encryptedPrefs.getString(KEY_TEACHER_EMAIL, null);
    }

    public String getTeacherName() {
        if (encryptedPrefs == null) {
            return null;
        }
        return encryptedPrefs.getString(KEY_TEACHER_NAME, null);
    }

    public void clearTeacherSession() {
        if (encryptedPrefs == null) {
            return;
        }
        // Remove teacher keys only — the encrypted file is shared with
        // student tokens, so a blanket clear() would sign the student out too.
        encryptedPrefs.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_TEACHER_ID)
                .remove(KEY_TEACHER_EMAIL)
                .remove(KEY_TEACHER_NAME)
                .apply();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Student session
    // ══════════════════════════════════════════════════════════════════════

    public void saveStudentEnrollment(String enrollmentNumber) {
        plainPrefs.edit()
                .putString(KEY_ENROLLMENT, enrollmentNumber)
                .apply();
    }

    public void saveStudentSession(String enrollmentNumber, String name, String accessToken, String refreshToken) {
        plainPrefs.edit()
                .putString(KEY_ENROLLMENT, enrollmentNumber)
                .putString(KEY_STUDENT_NAME, name != null ? name : "")
                .apply();
        if (encryptedPrefs == null) {
            Log.e(TAG, "Cannot save student tokens: encrypted storage unavailable");
            return;
        }
        encryptedPrefs.edit()
                .putString(KEY_STUDENT_ACCESS_TOKEN, accessToken)
                .putString(KEY_STUDENT_REFRESH_TOKEN, refreshToken)
                .apply();
    }

    public boolean hasStudentSession() {
        String enrollment = plainPrefs.getString(KEY_ENROLLMENT, null);
        return enrollment != null && !enrollment.isEmpty();
    }

    public String getStudentEnrollment() {
        return plainPrefs.getString(KEY_ENROLLMENT, null);
    }

    public String getStudentName() {
        return plainPrefs.getString(KEY_STUDENT_NAME, "Student");
    }

    public String getStudentAccessToken() {
        if (encryptedPrefs == null) {
            return null;
        }
        return encryptedPrefs.getString(KEY_STUDENT_ACCESS_TOKEN, null);
    }

    public void clearStudentSession() {
        plainPrefs.edit()
                .remove(KEY_ENROLLMENT)
                .remove(KEY_STUDENT_NAME)
                .apply();
        if (encryptedPrefs != null) {
            encryptedPrefs.edit()
                    .remove(KEY_STUDENT_ACCESS_TOKEN)
                    .remove(KEY_STUDENT_REFRESH_TOKEN)
                    .apply();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Full sign-out
    // ══════════════════════════════════════════════════════════════════════

    public void clearAll() {
        clearTeacherSession();
        clearStudentSession();
    }
}
