package com.arshad.studdy_app_android_only.network;

import com.arshad.studdy_app_android_only.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Singleton Retrofit client pre-configured for the Supabase REST API.
 *
 * Two static factory methods:
 *   • getAnonClient()          — anon/unauthenticated (student operations)
 *   • getAuthClient(token)     — Bearer = teacher's JWT access token
 *
 * The Supabase PostgREST base URL ends with /rest/v1/.
 * Auth endpoints are under /auth/v1/ — handled by a separate Auth-specific
 * Retrofit instance created via {@link #getAuthApiClient()}.
 */
public final class SupabaseClient {

    private static final String REST_BASE_URL =
            requireHttpBaseUrl(BuildConfig.SUPABASE_URL, "/rest/v1/");
    private static final String AUTH_BASE_URL =
            requireHttpBaseUrl(BuildConfig.SUPABASE_URL, "/auth/v1/");

    private static SupabaseClient instance;

    private final Retrofit anonRetrofit;

    /**
     * Validates the Supabase URL from local.properties before Retrofit ever
     * sees it. Without this check, an empty or placeholder URL fails deep
     * inside OkHttp with "Expected URL scheme 'http' or 'https' but no
     * scheme was found", which says nothing about the actual problem.
     * This fails fast with the real cause instead.
     *
     * Note: this does not make the app work without a URL — nothing can.
     * It only turns an obscure crash into an actionable error message.
     */
    private static String requireHttpBaseUrl(String supabaseUrl, String path) {
        if (supabaseUrl == null || supabaseUrl.isEmpty()
                || !(supabaseUrl.startsWith("http://") || supabaseUrl.startsWith("https://"))) {
            throw new IllegalStateException(
                    "SUPABASE_URL is missing or invalid. Add a valid https:// Supabase URL "
                            + "to local.properties (SUPABASE_URL=...) and rebuild the app.");
        }
        String base = supabaseUrl.endsWith("/")
                ? supabaseUrl.substring(0, supabaseUrl.length() - 1)
                : supabaseUrl;
        return base + path;
    }

    // Private constructor: build the anon Retrofit (no Bearer teacher token).
    private SupabaseClient() {
        OkHttpClient anonHttpClient = buildBaseHttpClient()
                .addInterceptor(new SupabaseHeaderInterceptor(null))
                .build();

        anonRetrofit = new Retrofit.Builder()
                .baseUrl(REST_BASE_URL)
                .client(anonHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    public static synchronized SupabaseClient getInstance() {
        if (instance == null) {
            instance = new SupabaseClient();
        }
        return instance;
    }

    // ── Anon Retrofit (students, unauthenticated reads) ────────────────────

    public Retrofit getAnonRetrofit() {
        return anonRetrofit;
    }

    // ── Authenticated Retrofit (teacher operations) ────────────────────────
    // A new Retrofit/OkHttpClient is built per-call so the token is current.

    public static Retrofit buildAuthRetrofit(String accessToken) {
        OkHttpClient httpClient = buildBaseHttpClient()
                .addInterceptor(new SupabaseHeaderInterceptor(accessToken))
                .authenticator(new SupabaseAuthenticator())
                .build();

        return new Retrofit.Builder()
                .baseUrl(REST_BASE_URL)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    // ── Auth endpoint Retrofit ─────────────────────────────────────────────
    // Points to /auth/v1/ for sign-in, sign-up, password reset.

    public static Retrofit getAuthApiClient() {
        OkHttpClient httpClient = buildBaseHttpClient()
                .addInterceptor(new SupabaseHeaderInterceptor(null))
                .build();

        return new Retrofit.Builder()
                .baseUrl(AUTH_BASE_URL)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    public static Retrofit getAuthApiClient(String accessToken) {
        OkHttpClient httpClient = buildBaseHttpClient()
                .addInterceptor(new SupabaseHeaderInterceptor(accessToken))
                .build();

        return new Retrofit.Builder()
                .baseUrl(AUTH_BASE_URL)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    // ── Convenience typed-service factories ───────────────────────────────

    public <T> T createAnonService(Class<T> serviceClass) {
        return anonRetrofit.create(serviceClass);
    }

    public static <T> T createAuthService(Class<T> serviceClass, String accessToken) {
        return buildAuthRetrofit(accessToken).create(serviceClass);
    }

    // ── OkHttpClient base builder ──────────────────────────────────────────

    private static OkHttpClient.Builder buildBaseHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS);

        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            builder.addInterceptor(logging);
        }

        return builder;
    }
}
