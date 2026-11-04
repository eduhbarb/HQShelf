package com.hqshelf.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object FileProviderUtil {
    const val AUTHORITY = "com.hqshelf.fileprovider"

    fun uriForFile(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, AUTHORITY, file)
}
