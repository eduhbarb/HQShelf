package com.hqshelf.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.hqshelf.util.FileProviderUtil
import java.io.File

/**
 * Atividade proxy lançada pelo widget. Recebe os dados do arquivo via extras,
 * monta o Intent ACTION_VIEW com FileProvider (concedendo permissão de leitura)
 * e abre o seletor nativo do Android para o usuário escolher o leitor
 * (CBR/CBZ, EPUB, PDF ou MOBI).
 */
class ReaderProxyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filePath = intent.getStringExtra("file_path") ?: run { finish(); return }
        val mimeType = intent.getStringExtra("mime_type") ?: "*/*"

        val file = File(filePath)
        if (!file.exists()) {
            finish()
            return
        }

        val uri = FileProviderUtil.uriForFile(this, file)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val chooser = Intent.createChooser(viewIntent, getString(com.hqshelf.R.string.open_with))
        startActivity(chooser)
        finish()
    }
}
