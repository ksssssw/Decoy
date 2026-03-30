package com.peekaboo.noop

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.peekaboo.core.PeekabooProvider

class NoOpPeekabooInitializer : ContentProvider() {
    override fun onCreate(): Boolean {
        PeekabooProvider.instance = NoOpPeekaboo()
        return true
    }
    override fun query(uri: Uri, p: Array<String>?, s: String?, sA: Array<String>?, so: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, sA: Array<String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, sA: Array<String>?): Int = 0
}
