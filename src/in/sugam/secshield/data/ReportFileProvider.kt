package `in`.sugam.secshield.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Minimal read-only ContentProvider that serves files from the app cache dir
 * (the PDF report) to a share chooser. No other directory is ever served:
 * the canonical path must stay inside the cache dir.
 */
class ReportFileProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        try {
            val name = uri.lastPathSegment
            if (name == null || name.contains("/") || name.contains("..")) {
                throw FileNotFoundException("bad name")
            }
            val cache = context!!.cacheDir
            val f = File(cache, name)
            val canonCache = cache.canonicalPath
            val canonFile = f.canonicalPath
            if (!canonFile.startsWith(canonCache + File.separator)) {
                throw FileNotFoundException("outside cache")
            }
            if (!f.exists()) throw FileNotFoundException("missing")
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: FileNotFoundException) {
            throw e
        } catch (t: Throwable) {
            throw FileNotFoundException("unavailable")
        }
    }

    override fun getType(uri: Uri): String? = "application/pdf"

    override fun query(uri: Uri, projection: Array<String>?, selection: String?,
                       selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<String>?): Int = 0
}
