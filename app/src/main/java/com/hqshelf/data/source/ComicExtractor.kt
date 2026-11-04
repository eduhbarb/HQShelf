package com.hqshelf.data.source

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile

object ComicExtractor {
    private val IMAGE_EXTENSIONS = listOf("jpg", "jpeg", "png", "webp")

    // Formatos aceitos na estante: quadrinhos (.cbz/.cbr) + livros (.epub/.pdf/.mobi).
    val SUPPORTED_EXTENSIONS = listOf("cbz", "cbr", "epub", "pdf", "mobi")

    data class ExtractedCover(val bitmap: Bitmap, val fileName: String)

    fun isSupported(fileName: String): Boolean {
        val name = fileName.lowercase()
        return SUPPORTED_EXTENSIONS.any { name.endsWith(".$it") }
    }

    /**
     * Resolve o MIME pelo extensão (contentResolver pode devolver octet-stream).
     * EPUB e MOBI ganham o tipo que leitores externos reconhecem.
     */
    fun mimeTypeFor(fileName: String, givenMime: String?): String {
        val name = fileName.lowercase()
        return when {
            name.endsWith(".pdf") -> "application/pdf"
            name.endsWith(".epub") -> "application/epub+zip"
            name.endsWith(".mobi") -> "application/x-mobipocket-ebook"
            name.endsWith(".cbz") -> "application/x-cbz"
            name.endsWith(".cbr") -> "application/x-cbr"
            givenMime?.isNotBlank() == true -> givenMime
            else -> "application/octet-stream"
        }
    }

    /**
     * Extrai a capa conforme o formato:
     * - `.cbz` (ZIP): primeira imagem alfabética.
     * - `.epub` (ZIP): item de capa declarado no OPF (cai para primeira imagem).
     * - `.cbr` (RAR): primeira imagem alfabética via junrar.
     * - `.pdf`: renderiza a primeira página com PdfRenderer.
     * - `.mobi`: varre os bytes procurando imagens JPEG/PNG embutidas.
     */
    fun extractCover(context: Context, file: File): ExtractedCover {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".cbr") -> extractFromRar(file)
            name.endsWith(".pdf") -> extractFromPdf(file)
            name.endsWith(".mobi") -> extractFromMobi(file)
            name.endsWith(".epub") -> extractFromEpub(file)
            else -> extractFromZip(file)
        }
    }

    // CBR: primeira imagem alfabética dentro do RAR (junrar).
    // junrar 7.5.5 é RAR4 puro (RAR5 lança UnsupportedRarV5Exception). O
    // seletor Android usa o mime application/octet-stream para .cbr, então
    // arquivos RAR5 não são barrados na UI — aqui eles dão UnsupportedRarV5Exception
    // e a mensagem explicativa abaixo.
    private fun extractFromRar(file: File): ExtractedCover {
        if (!file.canRead()) {
            throw IllegalStateException("Arquivo não legível: ${file.name}")
        }
        // Valida assinatura RAR para não tentar extrair ZIP como RAR.
        if (!hasRarSignature(file)) {
            throw IllegalStateException("Arquivo não é um RAR válido: ${file.name}")
        }
        return try {
            Archive(file).use { archive ->
                // junta headers de imagem, ordena alfabeticamente e extrai só a primeira.
                val images = mutableListOf<FileHeader>()
                var header = archive.nextFileHeader()
                while (header != null) {
                    if (!header.isDirectory && isImage(header.fileName)) {
                        images += header
                    }
                    header = archive.nextFileHeader()
                }
                val first = images.sortedBy { it.fileName.lowercase() }.firstOrNull()
                    ?: throw IllegalStateException("Nenhuma imagem encontrada no CBR")
                archive.getInputStream(first).use { input ->
                    val bitmap = BitmapFactory.decodeStream(input)
                        ?: throw IllegalStateException("Falha ao decodificar imagem do CBR")
                    return ExtractedCover(bitmap, first.fileName)
                }
            }
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: com.github.junrar.exception.UnsupportedRarV5Exception) {
            throw IllegalStateException(
                "CBR em formato RAR5, não suportado (junrar lê só RAR4). " +
                "Recompacte como RAR4 ou use .cbz.", e
            )
        } catch (e: Exception) {
            throw IllegalStateException("Falha ao ler CBR: ${e.message}", e)
        }
    }

    private fun hasRarSignature(file: File): Boolean {
        return try {
            file.inputStream().buffered().use { input ->
                val magic = ByteArray(8)
                val read = input.read(magic)
                if (read < 7) return false
                // "Rar!" + 1A 07 [00|01]
                magic[0] == 0x52.toByte() && magic[1] == 0x61.toByte() &&
                    magic[2] == 0x72.toByte() && magic[3] == 0x21.toByte() &&
                    magic[4] == 0x1A.toByte() && magic[5] == 0x07.toByte() &&
                    (magic[6] == 0x00.toByte() || magic[6] == 0x01.toByte())
            }
        } catch (_: Exception) {
            false
        }
    }

    // CBZ: primeira imagem alfabética dentro do ZIP.
    private fun extractFromZip(file: File): ExtractedCover {
        ZipFile(file).use { zip ->
            val entry = zip.entries().asSequence()
                .filter { !it.isDirectory }
                .filter { isImage(it.name) }
                .sortedBy { it.name.lowercase() }
                .firstOrNull()
                ?: throw IllegalStateException("Nenhuma imagem encontrada no CBZ")
            zip.getInputStream(entry).use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                    ?: throw IllegalStateException("Falha ao decodificar imagem do CBZ")
                return ExtractedCover(bitmap, entry.name)
            }
        }
    }

    // EPUB: capa declarada no OPF (meta cover / cover-image / guide), senão primeira imagem.
    private fun extractFromEpub(file: File): ExtractedCover {
        ZipFile(file).use { zip ->
            val entries = zip.entries().asSequence()
                .filter { !it.isDirectory }
                .toList()
            val opfPath = findOpfPath(zip, entries)
            val coverEntry = findEpubCoverEntry(zip, opfPath, entries)
            val target = coverEntry
                ?: entries.filter { isImage(it.name) }
                    .sortedBy { it.name.lowercase() }
                    .firstOrNull()
                ?: throw IllegalStateException("Nenhuma imagem encontrada no EPUB")
            zip.getInputStream(target).use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                    ?: throw IllegalStateException("Falha ao decodificar capa do EPUB")
                return ExtractedCover(bitmap, target.name)
            }
        }
    }

    /** Caminho do pacote OPF, via META-INF/container.xml ou primeiro *.opf. */
    private fun findOpfPath(zip: ZipFile, entries: List<java.util.zip.ZipEntry>): String? {
        val container = entries.firstOrNull { it.name.equals("META-INF/container.xml", ignoreCase = true) }
        if (container != null) {
            val xml = zip.getInputStream(container).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val match = Regex("""full-path\s*=\s*["']([^"']+)["']""").find(xml)
            val path = match?.groupValues?.get(1)?.trim()
            if (!path.isNullOrBlank() && zip.getEntry(path) != null) return path
        }
        return entries.firstOrNull { it.name.lowercase().endsWith(".opf") }?.name
    }

    /** Procura o item de capa declarado no OPF: meta cover, cover-image ou guide reference. */
    private fun findEpubCoverEntry(
        zip: ZipFile,
        opfPath: String?,
        entries: List<java.util.zip.ZipEntry>
    ): java.util.zip.ZipEntry? {
        if (opfPath == null) return null
        val opfEntry = zip.getEntry(opfPath) ?: return null
        val opf = zip.getInputStream(opfEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val baseDir = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
        val candidates = mutableListOf<String>()

        // EPUB2: <meta name="cover" content="ID"/> -> manifest item id=ID
        for (meta in Regex("""<meta[^>]*>""").findAll(opf)) {
            val tag = meta.value
            if (attr(tag, "name").equals("cover", ignoreCase = true)) {
                val id = attr(tag, "content")
                if (!id.isNullOrBlank()) itemHref(opf, id)?.let { candidates += it }
            }
        }
        // EPUB3: <item properties="cover-image" href="..."/>
        for (item in Regex("""<item[^>]*>""").findAll(opf)) {
            val tag = item.value
            val properties = attr(tag, "properties") ?: ""
            if ("cover-image" in properties.split(Regex("""\s+"""))) {
                attr(tag, "href")?.let { candidates += it }
            }
        }
        // Guia: <reference type="cover" href="..."/>
        for (ref in Regex("""<reference[^>]*>""").findAll(opf)) {
            val tag = ref.value
            if (attr(tag, "type").equals("cover", ignoreCase = true)) {
                attr(tag, "href")?.let { candidates += it }
            }
        }
        return candidates.firstNotNullOfOrNull { href ->
            entries.firstOrNull { it.name.equals(resolveEpubPath(baseDir, href), ignoreCase = true) }
                ?: entries.firstOrNull { it.name.endsWith(resolveEpubPath(baseDir, href).substringAfterLast('/')) }
        }
    }

    /** href do item do manifest com id [id]; null se não achar. */
    private fun itemHref(opf: String, id: String): String? {
        for (item in Regex("""<item[^>]*>""").findAll(opf)) {
            val tag = item.value
            if (attr(tag, "id").equals(id, ignoreCase = true)) {
                return attr(tag, "href")
            }
        }
        return null
    }

    /** Extrai atributo XML attr="valor" (ordem qualquer). */
    private fun attr(tag: String, name: String): String? =
        Regex("""\b""" + Regex.escape(name) + """\s*=\s*["']([^"']*)["']""")
            .find(tag)?.groupValues?.get(1)

    /** Resolve href relativo ao OPF (remove fragmento, decodifica %20, normaliza ../). */
    private fun resolveEpubPath(baseDir: String, href: String): String {
        var path = href.substringBefore('#').substringBefore('?')
        path = URLDecoder.decode(path, "UTF-8")
        if (path.startsWith("/")) return path.trimStart('/')
        val combined = if (baseDir.isBlank()) path else "$baseDir/$path"
        val parts = mutableListOf<String>()
        for (p in combined.split('/')) {
            when (p) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += p
            }
        }
        return parts.joinToString("/")
    }

    // PDF: renderiza a página 1 (PdfRenderer embutido, sem dependência externa).
    private fun extractFromPdf(file: File): ExtractedCover {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount == 0) throw IllegalStateException("PDF sem páginas")
                renderer.openPage(0).use { page ->
                    // Largura 600px, altura proporcional; depois o repositório
                    // redimensiona para a miniatura 300x450.
                    // clip = região da página (em pontos), aqui a página inteira.
                    val destWidth = 600
                    val destHeight = maxOf(1, (destWidth * page.height / maxOf(1, page.width)))
                    val dest = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888)
                    val clip = android.graphics.Rect(0, 0, page.width, page.height)
                    page.render(dest, clip, null, 0)
                    return ExtractedCover(dest, file.name)
                }
            }
        }
    }

    // MOBI: forma mais simples — varre os bytes das imagens JPEG/PNG embutidas
    // e fica com a maior imagem decodificável (capa costuma ser a maior).
    private fun extractFromMobi(file: File): ExtractedCover {
        val bytes = file.readBytes()
        val jpegStart = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val jpegEnd = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        val pngStart = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
        val pngEnd = byteArrayOf(
            0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte()
        )
        var best: Pair<Bitmap, Int>? = null
        var examined = 0

        fun consider(start: Int, endExclusive: Int): Boolean {
            if (examined++ > 200 || endExclusive <= start) return false
            val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, start, endExclusive - start) }.getOrNull()
                ?: return false
            if (bitmap.width <= 0 || bitmap.height <= 0) return false
            val pixels = bitmap.width * bitmap.height
            val current = best
            if (current == null || pixels > current.second) {
                current?.first?.recycle()
                best = bitmap to pixels
            } else {
                bitmap.recycle()
            }
            return true
        }

        var i = 0
        while (i < bytes.size - 4) {
            when {
                startsWith(bytes, i, jpegStart) -> {
                    val end = indexOf(bytes, jpegEnd, i + jpegStart.size)
                    if (end >= 0) {
                        consider(i, end + jpegEnd.size)
                        i = end + jpegEnd.size
                    } else {
                        i++
                    }
                }
                startsWith(bytes, i, pngStart) -> {
                    val end = indexOf(bytes, pngEnd, i + pngStart.size)
                    if (end >= 0) {
                        consider(i, end + pngEnd.size)
                        i = end + pngEnd.size
                    } else {
                        i++
                    }
                }
                else -> i++
            }
        }
        return best?.let { ExtractedCover(it.first, file.name) }
            ?: throw IllegalStateException("Nenhuma imagem encontrada no MOBI")
    }

    private fun startsWith(bytes: ByteArray, offset: Int, prefix: ByteArray): Boolean {
        if (offset + prefix.size > bytes.size) return false
        for (k in prefix.indices) if (bytes[offset + k] != prefix[k]) return false
        return true
    }

    private fun indexOf(bytes: ByteArray, needle: ByteArray, from: Int): Int {
        var i = from
        while (i + needle.size <= bytes.size) {
            if (startsWith(bytes, i, needle)) return i
            i++
        }
        return -1
    }

    private fun isImage(name: String?): Boolean =
        name?.let { IMAGE_EXTENSIONS.any { it.lowercase().endsWith(it) } } ?: false

    /**
     * Normaliza a capa para 2:3 exato (largura:altura), como moldura "cover":
     * recorta o centro para a proporção 2:3 e escala para [maxWidth]x[maxHeight]
     * (uso atual 300x450). Toda capa fica harmonica, mesmo de fontes com outras
     * proporções; nada é esticado nem sobra faixa vazia.
     */
    fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        if (bitmap.width <= 0 || bitmap.height <= 0) return bitmap
        val targetAspect = 2f / 3f
        val sourceAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        val cropWidth: Int
        val cropHeight: Int
        if (sourceAspect > targetAspect) {
            // Mais larga que 2:3: recorta as laterais, mantém a altura cheia.
            cropHeight = bitmap.height
            cropWidth = (cropHeight * targetAspect).toInt().coerceAtLeast(1)
        } else {
            // Mais alta que 2:3: recorta topo/base, mantém a largura cheia.
            cropWidth = bitmap.width
            cropHeight = (cropWidth / targetAspect).toInt().coerceAtLeast(1)
        }
        val cropX = ((bitmap.width - cropWidth) / 2).coerceAtLeast(0)
        val cropY = ((bitmap.height - cropHeight) / 2).coerceAtLeast(0)
        val cropped = if (cropWidth == bitmap.width && cropHeight == bitmap.height) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, cropX, cropY, cropWidth, cropHeight)
        }
        if (cropped.width == maxWidth && cropped.height == maxHeight) return cropped
        return Bitmap.createScaledBitmap(cropped, maxWidth, maxHeight, true)
    }

    /** Salva a miniatura no armazenamento privado persistente do app e retorna o caminho absoluto. */
    fun saveThumbnail(context: Context, bitmap: Bitmap, prefix: String): String {
        val file = File(context.filesDir, "${prefix}_${System.currentTimeMillis()}.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 80, out)
        }
        return file.absolutePath
    }
}
