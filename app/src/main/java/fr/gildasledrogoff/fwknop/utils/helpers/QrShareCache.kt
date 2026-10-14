// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.utils.helpers

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QrShareCache @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val directory: File get() = File(context.cacheDir, DIRECTORY)
    fun newFile(): File {
        clear()
        return File(directory.apply { mkdirs() }, "qr-${UUID.randomUUID()}.png")
    }

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun clear() {
        directory.listFiles()?.forEach(File::delete)
    }

    private companion object {
        const val DIRECTORY = "qr"
    }
}
