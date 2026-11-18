package com.example.data.cloud

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

fun toFriendlyFirestoreMessage(exception: Throwable): String {
    val msg = exception.message.orEmpty()
    return when {
        msg.contains("Quota exceeded", ignoreCase = true) ->
            "تم الوصول إلى الحد اليومي المجاني لقاعدة البيانات السحابية. سيتم التصفير غداً."
        msg.contains("PERMISSION_DENIED", ignoreCase = true) ||
            msg.contains("Missing or insufficient permissions", ignoreCase = true) ->
            "تعذر الوصول للبيانات السحابية — يرجى التحقق من تسجيل الدخول بحساب Google."
        msg.contains("UNAVAILABLE", ignoreCase = true) ||
            msg.contains("network", ignoreCase = true) ->
            "لا يوجد اتصال بالإنترنت حالياً — التغييرات محفوظة محلياً وستُزامَن تلقائياً."
        else -> "تعذر إتمام المزامنة السحابية حالياً، حاول مرة أخرى."
    }
}
