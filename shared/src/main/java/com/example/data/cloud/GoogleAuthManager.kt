package com.example.data.cloud

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object GoogleAuthManager {
    private const val TAG = "GoogleAuthManager"

    suspend fun attemptSilentSignIn(
        activityContext: Context,
        serverClientId: String
    ): FirebaseUser? {
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.let { return it }
        if (serverClientId.isBlank()) return null

        return try {
            val credentialManager = CredentialManager.create(activityContext)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = credentialManager.getCredential(
                context = activityContext,
                request = request
            )
            signInWithCredentialResult(auth, result.credential)
        } catch (_: NoCredentialException) {
            null
        } catch (_: GetCredentialCancellationException) {
            null
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Silent Google sign-in skipped: ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Silent Google sign-in error: ${e.message}")
            null
        }
    }

    suspend fun signInInteractive(
        activityContext: Context,
        serverClientId: String
    ): Result<FirebaseUser> {
        if (serverClientId.isBlank()) {
            return Result.failure(IllegalStateException("إعدادات تسجيل الدخول عبر Google غير مكتملة."))
        }
        val auth = FirebaseAuth.getInstance()
        val credentialManager = CredentialManager.create(activityContext)

        return try {
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()
            val result = credentialManager.getCredential(
                context = activityContext,
                request = request
            )
            val user = signInWithCredentialResult(auth, result.credential)
                ?: return Result.failure(IllegalStateException("تعذر التحقق من حساب Google"))
            Result.success(user)
        } catch (_: GetCredentialCancellationException) {
            Result.failure(UserCancelledSignInException())
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google account found on device: ${e.message}")
            Result.failure(IllegalStateException("يرجى إضافة حساب Google في إعدادات الجهاز للمتابعة."))
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google sign-in credential error", e)
            Result.failure(IllegalStateException("تعذر تسجيل الدخول عبر Google: ${e.localizedMessage ?: "خطأ غير متوقع"}"))
        } catch (e: Exception) {
            Log.e(TAG, "Google sign-in failed", e)
            Result.failure(IllegalStateException("تعذر الاتصال بخدمة الحسابات، حاول مجدداً."))
        }
    }

    private suspend fun signInWithCredentialResult(
        auth: FirebaseAuth,
        credential: androidx.credentials.Credential
    ): FirebaseUser? {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
            val authResult = auth.signInWithCredential(firebaseCred).await()
            return authResult.user
        }
        return null
    }

    suspend fun signOut(context: Context) {
        withContext(NonCancellable) {
            runCatching { FirebaseAuth.getInstance().signOut() }
            runCatching {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            }
        }
    }
}

class UserCancelledSignInException : Exception("تم إلغاء تسجيل الدخول")
