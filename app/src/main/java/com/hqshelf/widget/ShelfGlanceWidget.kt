package com.hqshelf.widget

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import com.hqshelf.R
import com.hqshelf.data.local.db.AppDatabase
import com.hqshelf.data.local.db.ShelfItem
import com.hqshelf.ui.viewmodel.ShelfStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Widget da estante (Glance) — superfície transparente, sem fundo.
 *
 * A prateleira é a PNG original (2952x1440) exibida inteira, com contain, centralizada na
 * horizontal e ancorada na base. As capas ficam em pé sobre o topo da tábua (linha em ~61% da
 * altura da imagem), sempre com 70x97dp: só a imagem acompanha o tamanho do widget.
 * Cada capa abre o [ReaderProxyActivity] com o leitor externo.
 */
class ShelfGlanceWidget : GlanceAppWidget() {

    // LocalSize recebe o tamanho real do widget (e é reemitido quando ele é redimensionado).
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = AppDatabase.getDatabase(context).shelfItemDao()
        val initialItems = withContext(Dispatchers.IO) { dao.getAll() }
        val itemsFlow = dao.observeAll()
        // Estilo observado reativamente: mudança no app recompõe o widget sem remover/adicionar.
        val initialStyle = readShelfStyle(context)
        val styleFlow = observeShelfStyle(context)

        provideContent {
            // Estilo reativo: ao trocar no app, o Flow emite e o widget redesenha na hora.
            val estiloAtual by styleFlow.collectAsState(initial = initialStyle)
            val prefs = context.getSharedPreferences("hqshelf_prefs", Context.MODE_PRIVATE)
            // Número de versão que incrementa a cada mudança — força o launcher a repintar a view
            val renderVersion = prefs.getInt("widget_render_version", 0)
            Log.d("ShelfWidget", "Rendering estilo=$estiloAtual version=$renderVersion")

            // Observa o banco: a sessão Glance é reaproveitada entre updates, então reler
            // apenas em provideGlance deixaria o widget com dados velhos.
            val items by itemsFlow.collectAsState(initial = initialItems)
            val widgetSize = LocalSize.current
            val size = if (widgetSize.width.value.isNaN() || widgetSize.height.value.isNaN()) {
                DpSize(250.dp, 118.dp)
            } else {
                widgetSize
            }

            // Geometria do "contain": a imagem inteira, sem corte nem esticamento.
            val aspect = SHELF_WIDTH_PX / SHELF_HEIGHT_PX
            val imageWidth = if (size.width.value / size.height.value > aspect) {
                size.height * aspect
            } else {
                size.width
            }
            val imageHeight = imageWidth / aspect

            // Base das capas = topo da tábua. Distância até a base do widget (a imagem está
            // ancorada nela) = (1 - 0.6069) da altura renderizada da imagem.
            val coverBottomInset = imageHeight * (1f - BOARD_TOP_RATIO)
            val covers = items.take(MAX_COVERS)

            // Capas em proporção 2:3. Quando a estante está cheia (5 HQs) encolhe 10%
            // e o espaçamento entre elas cai 50% (12dp → 6dp).
            val isFive = items.size == MAX_COVERS
            val scale = if (isFive) 0.9f else 1f
            val coverWidth = (COVER_WIDTH_DP * scale).dp
            val coverHeight = (COVER_HEIGHT_DP * scale).dp
            val coverGap = if (isFive) 6f else COVER_GAP_DP

            // Sem .background(...) na raiz: superfície transparente.
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                // 1. Prateleira primeiro (fica atrás): apenas o objeto selecionado é renderizado
                if (estiloAtual == ShelfStyle.CARVALHO) {
                    Image(
                        provider = ImageProvider(R.drawable.shelf_carvalho),
                        contentDescription = "Estante Carvalho v$renderVersion",
                        modifier = GlanceModifier.size(width = imageWidth, height = imageHeight),
                        contentScale = ContentScale.Fit
                    )
                }
                if (estiloAtual == ShelfStyle.BRANCO) {
                    Image(
                        provider = ImageProvider(R.drawable.shelf_branco),
                        contentDescription = "Estante Branco v$renderVersion",
                        modifier = GlanceModifier.size(width = imageWidth, height = imageHeight),
                        contentScale = ContentScale.Fit
                    )
                }
                if (estiloAtual == ShelfStyle.PRETO) {
                    Image(
                        provider = ImageProvider(R.drawable.shelf_preto),
                        contentDescription = "Estante Preto v$renderVersion",
                        modifier = GlanceModifier.size(width = imageWidth, height = imageHeight),
                        contentScale = ContentScale.Fit
                    )
                }

                // 2. Capas depois (ficam na frente da prateleira)
                if (covers.isNotEmpty()) {
                    Row(
                        modifier = GlanceModifier.padding(bottom = coverBottomInset),
                        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                        verticalAlignment = Alignment.Vertical.Bottom
                    ) {
                        covers.forEachIndexed { index, item ->
                            if (index > 0) Spacer(modifier = GlanceModifier.width(coverGap.dp))
                            Cover(
                                context = context,
                                item = item,
                                width = coverWidth,
                                height = coverHeight
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val SHELF_WIDTH_PX = 886f
        const val SHELF_HEIGHT_PX = 432f

        // Topo da tábua na PNG original: y = 874 de 1440.
        const val BOARD_TOP_RATIO = 874f / 1440f

        const val MAX_COVERS = 5
        // Proporção 2:3 (largura:altura).
        val COVER_WIDTH_DP = 70f
        val COVER_HEIGHT_DP = 105f
        val COVER_GAP_DP = 12f
    }
}

/** Lê o estilo salvo pelo app (SharedPreferences). */
private fun readShelfStyle(context: Context): ShelfStyle {
    val name = context.getSharedPreferences("hqshelf_prefs", Context.MODE_PRIVATE)
        .getString("shelf_style", null)
    return when (name) {
        "BRANCO" -> ShelfStyle.BRANCO
        "PRETO" -> ShelfStyle.PRETO
        else -> ShelfStyle.CARVALHO
    }
}

/**
 * Flow reativo do estilo: emite a cada mudança em "hqshelf_prefs".
 * O listener mantém referência forte enquanto a sessão Glance coleta o Flow.
 */
private fun observeShelfStyle(context: Context): Flow<ShelfStyle> = callbackFlow {
    val prefs = context.getSharedPreferences("hqshelf_prefs", Context.MODE_PRIVATE)
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        trySend(readShelfStyle(context))
    }
    prefs.registerOnSharedPreferenceChangeListener(listener)
    awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
}

/** Capa individual: proporção 2:3, dimensões definidas pelo widget. */
@Composable
private fun Cover(
    context: Context,
    item: ShelfItem,
    width: Dp,
    height: Dp
) {
    val launchIntent = Intent(context, ReaderProxyActivity::class.java).apply {
        putExtra("file_path", item.localFilePath)
        putExtra("mime_type", item.mimeType)
    }
    val bitmap = item.thumbnailPath?.let { path ->
        runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    }

    if (bitmap != null) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = item.fileName,
            modifier = GlanceModifier.size(width = width, height = height)
                .clickable(actionStartActivity(launchIntent)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = GlanceModifier.size(width = width, height = height)
                .clickable(actionStartActivity(launchIntent)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "+")
        }
    }
}
