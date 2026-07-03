package com.peekaboo.android

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.peekaboo.core.MockRepository
import com.peekaboo.core.PeekabooProvider

public class PeekabooInitializer : ContentProvider() {
    override fun onCreate(): Boolean {
        // A dev tool must never crash the host app — fail quietly and log instead.
        runCatching {
            val appContext = context!!
            MockRepository.attachStorage(FileRuleStorage(appContext.filesDir))
            val peekaboo = RealPeekaboo(PeekabooServer(loadAppInfo()))
            peekaboo.start()
            PeekabooProvider.instance = peekaboo
        }.onFailure {
            android.util.Log.e("Peekaboo", "Failed to start Peekaboo inspector", it)
        }
        return true
    }
    private fun loadAppInfo(): AppInfo {
        val ctx = context!!
        val pkg = ctx.packageName
        val info = runCatching { ctx.packageManager.getPackageInfo(pkg, 0) }.getOrNull()
        val versionCode = info?.let {
            if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode
            else @Suppress("DEPRECATION") it.versionCode.toLong()
        } ?: 0L
        return AppInfo(
            packageName = pkg,
            appVersion = info?.versionName ?: "?",
            versionCode = versionCode,
            deviceModel = android.os.Build.MODEL ?: "unknown",
            sdkInt = android.os.Build.VERSION.SDK_INT,
        )
    }

    override fun query(uri: Uri, p: Array<String>?, s: String?, sA: Array<String>?, so: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, sA: Array<String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, sA: Array<String>?): Int = 0
}
