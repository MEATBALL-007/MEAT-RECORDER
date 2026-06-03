package com.example.recorderproject.network

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class GoogleDriveUploader(private val context: Context) {
    private val TAG = "GoogleDriveUploader"
    private val SCOPE = "oauth2:https://www.googleapis.com/auth/drive.file"

    suspend fun upload(file: File): String? = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
                ?: return@withContext null.also { Log.w(TAG, "Not signed in") }

            val token = GoogleAuthUtil.getToken(context, account.account!!, SCOPE)
            val boundary = "meatrec_${System.currentTimeMillis()}"
            val metaJson = """{"name":"${file.name}"}"""

            val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 60_000
            }

            conn.outputStream.buffered().use { out ->
                out.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metaJson\r\n--$boundary\r\nContent-Type: audio/wav\r\n\r\n".toByteArray())
                file.inputStream().use { it.copyTo(out) }
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }

            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                .bufferedReader().readText()

            if (code in 200..299) {
                val id = JSONObject(body).optString("id")
                Log.d(TAG, "Uploaded ${file.name} → Drive id=$id")
                id
            } else {
                Log.e(TAG, "Upload failed HTTP $code: $body")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload exception: ${e.message}", e)
            null
        }
    }

    fun isSignedIn(): Boolean =
        GoogleSignIn.getLastSignedInAccount(context) != null

    fun getSignedInEmail(): String? =
        GoogleSignIn.getLastSignedInAccount(context)?.email
}
