package com.hqshelf.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.hqshelf.domain.model.Comic
import com.hqshelf.ui.viewmodel.ShelfStyle
import com.hqshelf.ui.viewmodel.ShelfViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// Compatibilidade: cores antigas redirecionadas para a paleta Material 3.
val ShelfColorPrimary = Color(0xFF7D4A2E)
val ShelfColorOnSurface = Color(0xFF211A12)
val ShelfColorOnSurfaceVariant = Color(0xFF524338)

// Linha do topo da tábua na prateleira (mesma razão do widget: 874/1440 da PNG original).
private const val BOARD_TOP_RATIO = 874f / 1440f

/**
 * Paleta Material 3 para a estante — fundo claro quente, primário marrom.
 * Slots, tipografia e elevação seguem os tokens do Material 3.
 */
private val ShelfLight = lightColorScheme(
    primary = Color(0xFF7D4A2E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBC9),
    onPrimaryContainer = Color(0xFF2F1201),
    secondary = Color(0xFF66574C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEFDCCC),
    onSecondaryContainer = Color(0xFF20160D),
    background = Color(0xFFFFF8F1),
    surface = Color(0xFFFFF8F1),
    surfaceVariant = Color(0xFFF3E1D4),
    onSurface = Color(0xFF211A12),
    onSurfaceVariant = Color(0xFF524338),
    outline = Color(0xFF847366),
    outlineVariant = Color(0xFFD8C2B4)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(viewModel: ShelfViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    var addTarget by remember { mutableStateOf<Int?>(null) }
    // Slot da capa que está sendo arrastado (para reordenação).
    var dragSource by remember { mutableStateOf<Int?>(null) }

    val addLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val position = addTarget ?: return@let
            addTarget = null
            viewModel.addComic(it, position)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    LaunchedEffect(viewModel.error) {
        viewModel.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    MaterialTheme(
        colorScheme = ShelfLight,
        typography = Typography()
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "HQShelf",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    colors = TopAppBarDefaults.mediumTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(id = com.hqshelf.R.string.shelf_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                Shelf(
                    modifier = Modifier.fillMaxWidth().height(218.dp),
                    style = viewModel.shelfStyle
                ) {
                    val shelf = viewModel.shelf
                    // Capas em proporção 2:3. Quando a estante está cheia (5 HQs) encolhe 10%
                    // e o espaçamento entre elas cai 50% (8dp → 4dp).
                    val isFive = shelf.size == 5
                    val coverHeightDp = if (isFive) 138f * 0.9f else 138f
                    val horizontalSpacingDp = if (isFive) 4f else 8f
                    // Distância entre centros de slots vizinhos (alvo do arrasto de reordenação).
                    val pitchDp = coverHeightDp * 2f / 3f + horizontalSpacingDp
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(horizontalSpacingDp.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        (1..5).forEach { position ->
                            val comic = shelf.firstOrNull { it.position == position }
                            val seqIndex = shelf.indexOfFirst { it.position == position }
                            ShelfSlot(
                                position = position,
                                comic = comic,
                                seqIndex = seqIndex,
                                seqCount = shelf.size,
                                isDragging = dragSource == seqIndex,
                                pitchDp = pitchDp,
                                onRemove = { viewModel.remove(position) },
                                onAdd = {
                                    addTarget = position
                                    addLauncher.launch(
                                        com.hqshelf.data.repository.ShelfRepositoryImpl.OPEN_MIME_TYPES
                                    )
                                },
                                onReorder = { from, to ->
                                    dragSource = null
                                    viewModel.reorder(from, to)
                                },
                                onDragStart = { dragSource = seqIndex },
                                modifier = Modifier.weight(1f).height(coverHeightDp.dp)
                            )
                        }
                    }
                }

                // Seletor de aparência da prateleira (abaixo das HQs).
                ShelfStyleSelector(viewModel = viewModel)

                Spacer(modifier = Modifier.height(20.dp))

                // Slot 1 vazio destacado como ação principal Material 3.
                val firstEmpty = (1..5).firstOrNull { pos -> viewModel.shelf.none { it.position == pos } }
                if (firstEmpty != null) {
                    Button(
                        onClick = {
                            addTarget = firstEmpty
                            addLauncher.launch(
                                com.hqshelf.data.repository.ShelfRepositoryImpl.OPEN_MIME_TYPES
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = stringResource(id = com.hqshelf.R.string.shelf_add_cta),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (viewModel.shelf.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(id = com.hqshelf.R.string.shelf_empty),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = com.hqshelf.R.string.shelf_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Prateleira de madeira com aparência escolhida (carvalho, branco ou preto). */
@Composable
private fun Shelf(
    modifier: Modifier = Modifier,
    style: ShelfStyle,
    content: @Composable BoxScope.() -> Unit
) {
    val drawable = when (style) {
        ShelfStyle.CARVALHO -> com.hqshelf.R.drawable.shelf_carvalho
        ShelfStyle.BRANCO -> com.hqshelf.R.drawable.shelf_branco
        ShelfStyle.PRETO -> com.hqshelf.R.drawable.shelf_preto
        else -> com.hqshelf.R.drawable.shelf_carvalho
    }

    // Altura renderizada da prateleira (medida após o primeiro layout).
    var boardHeightPx by remember { mutableStateOf(0f) }
    // Inset do fundo: deixa o fundo das capas alinhado com a linha do topo da tábua,
    // igual ao widget (capa em pé sobre a prateleira).
    val coverBottomInsetDp = if (boardHeightPx > 0f) {
        with(LocalDensity.current) {
            (boardHeightPx * (1f - BOARD_TOP_RATIO)).toDp()
        }
    } else {
        0.dp
    }

    Box(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
    ) {
        // Prateleira ao fundo, ancorada na base da caixa.
        Image(
            painter = painterResource(id = drawable),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .onSizeChanged { boardHeightPx = it.height.toFloat() }
        )
        // Capas em pé sobre a linha do topo da tábua (igual ao widget).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = coverBottomInsetDp, start = 10.dp, end = 10.dp)
                .align(Alignment.BottomCenter)
        ) {
            content()
        }
    }
}

/** Radio buttons para escolher a aparência da prateleira. */
@Composable
private fun ShelfStyleSelector(viewModel: ShelfViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = com.hqshelf.R.string.shelf_style_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShelfStyle.values().forEach { style: ShelfStyle ->
                val label = when (style) {
                    ShelfStyle.CARVALHO -> com.hqshelf.R.string.shelf_style_carvalho
                    ShelfStyle.BRANCO -> com.hqshelf.R.string.shelf_style_branco
                    ShelfStyle.PRETO -> com.hqshelf.R.string.shelf_style_preto
                    else -> com.hqshelf.R.string.shelf_style_carvalho
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = viewModel.shelfStyle == style,
                        onClick = { viewModel.setShelfStyle(style) },
                        colors = RadioButtonDefaults.colors(
                            unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = stringResource(id = label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { viewModel.forcarAtualizacaoWidget() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Text(
                text = stringResource(id = com.hqshelf.R.string.shelf_force_update),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

/**
 * Slot clicável — mesmo comportamento de adicionar/remover.
 * Sombras, cantos arredondados e botões Material 3 substituem "+"/"×" crus.
 */
@Composable
private fun ShelfSlot(
    position: Int,
    comic: Comic?,
    seqIndex: Int = -1,
    seqCount: Int = 0,
    onRemove: () -> Unit = {},
    onAdd: () -> Unit = {},
    onReorder: (Int, Int) -> Unit = { _, _ -> },
    onDragStart: (Int) -> Unit = {},
    isDragging: Boolean = false,
    pitchDp: Float = 0f,
    modifier: Modifier = Modifier
) {
    val slotBg = MaterialTheme.colorScheme.secondaryContainer
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current
    var dragOffset by remember { mutableStateOf(0f) }

    if (comic != null) {
        val thumbPath = comic.thumbnailPath
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(thumbPath) {
            if (thumbPath != null) {
                withContext(Dispatchers.IO) {
                    val b = BitmapFactory.decodeFile(thumbPath)
                    withContext(Dispatchers.Main) { bitmap = b }
                }
            }
        }
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .zIndex(if (isDragging) 1f else 0f)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { onDragStart(seqIndex) },
                        onDrag = { change, dragAmount ->
                            dragOffset += with(density) { dragAmount.x.toDp().value }
                            change.consume()
                        },
                        onDragEnd = {
                            val shift = (dragOffset / pitchDp).roundToInt()
                            val last = (seqCount - 1).coerceAtLeast(0)
                            val targetSeq = (seqIndex + shift).coerceIn(0, last)
                            dragOffset = 0f
                            if (targetSeq != seqIndex) onReorder(seqIndex, targetSeq)
                        },
                        onDragCancel = { dragOffset = 0f }
                    )
                }
                .offset(x = dragOffset.dp)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = comic.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }
            }
            // Remover: botão Material 3 com fundo legível.
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(28.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(text = "×", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        Box(
            modifier = modifier
                .shadow(2.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(slotBg)
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "+",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Adicionar",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
