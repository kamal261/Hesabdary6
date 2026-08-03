package com.kamal.smsfinance.util

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wraps Google Sign-In scoped to Drive's "appdata" folder (a hidden,
 * per-app space -- the user's other Drive files are never touched). Only
 * used for the optional cloud backup feature; SMS parsing itself never
 * needs network or Google account access.
 *
 * Setup required before this works:
 * 1. Create an OAuth 2.0 Android client ID in Google Cloud Console for this
 *    app's package name + SHA-1 signing fingerprint.
 * 2. Enable the Google Drive API on that Cloud project.
 * No client secret or server component is needed for this on-device flow.
 */
object GoogleSignInHelper {

    private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

    fun getClient(context: Context): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_APPDATA_SCOPE))
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    fun signInIntent(context: Context): Intent = getClient(context).signInIntent

    fun lastSignedInAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    fun handleSignInResult(data: Intent?): GoogleSignInAccount? =
        try {
            GoogleSignIn.getSignedInAccountFromIntent(data).result
        } catch (e: Exception) {
            null
        }

    fun signOut(context: Context) {
        getClient(context).signOut()
    }

    /**
     * Fetches a short-lived OAuth access token for the signed-in account,
     * scoped to the Drive appdata folder. Must be called off the main thread.
     */
    suspend fun getAccessToken(context: Context, account: GoogleSignInAccount): String? =
        withContext(Dispatchers.IO) {
            try {
                val androidAccount = Account(account.email, "com.google")
                GoogleAuthUtil.getToken(context, androidAccount, "oauth2:$DRIVE_APPDATA_SCOPE")
            } catch (e: Exception) {
                null
            }
        }
}
