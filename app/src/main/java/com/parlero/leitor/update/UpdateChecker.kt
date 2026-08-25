package com.parlero.leitor.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.parlero.leitor.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val version: String, val downloadUrl: String, val notes: String)

/**
 * Verifica a última release do repositório GitHub, baixa o APK e abre o instalador do Android.
 * Não usa nenhuma biblioteca externa (só HttpURLConnection/org.json, já embutidos no Android).
 */
class UpdateChecker(private val context: Context) {

    /** Retorna informações da atualização se a última release do GitHub for mais nova que a instalada. */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        val connection = URL(
            "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest"
        ).openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null

            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val remoteVersion = json.getString("tag_name").removePrefix("v")
            if (!isNewer(remoteVersion, BuildConfig.VERSION_NAME)) return@withContext null

            val assets = json.getJSONArray("assets")
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }
            apkUrl?.let { UpdateInfo(remoteVersion, it, json.optString("body", "")) }
        } finally {
            connection.disconnect()
        }
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val l = local.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }

    /** Baixa o APK da release para a pasta de cache do app, reportando progresso 0-100. */
    suspend fun downloadApk(downloadUrl: String, onProgress: (Int) -> Unit = {}): File =
        withContext(Dispatchers.IO) {
            val connection = URL(downloadUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connect()
            val total = connection.contentLength
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outFile = File(updatesDir, "parlero-update.apk")
            connection.inputStream.use { input ->
                outFile.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var downloaded = 0
                    var read = input.read(buffer)
                    while (read >= 0) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) onProgress((downloaded * 100L / total).toInt())
                        read = input.read(buffer)
                    }
                }
            }
            connection.disconnect()
            outFile
        }

    /** true se o app já tem permissão para instalar pacotes; false se precisa pedir nas Configurações do sistema. */
    fun canRequestPackageInstalls(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Abre a tela do sistema onde o usuário libera "instalar apps desconhecidos" para este app. */
    fun requestInstallPermissionIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )

    /** Abre o instalador padrão do Android para o APK baixado. */
    fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
