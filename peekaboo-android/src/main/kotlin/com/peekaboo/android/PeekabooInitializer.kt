package com.peekaboo.android

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.peekaboo.core.PeekabooProvider

class PeekabooInitializer : ContentProvider() {
    override fun onCreate(): Boolean {
        val interceptor = PeekabooInterceptor()
        val server = PeekabooServer(context!!)
        val peekaboo = RealPeekaboo(interceptor, server)
        peekaboo.start()
        PeekabooProvider.instance = peekaboo
        return true
    }
    override fun query(uri: Uri, p: Array<String>?, s: String?, sA: Array<String>?, so: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, sA: Array<String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, sA: Array<String>?): Int = 0
}
