package com.programmersbox.uiviews.utils

import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.accompanist.placeholder.material.placeholder
import com.programmersbox.uiviews.R
import com.skydoves.landscapist.glide.GlideImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

fun Modifier.fadeInAnimation(): Modifier = composed {
    val animatedProgress = remember { Animatable(initialValue = 0f) }
    LaunchedEffect(Unit) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(600)
        )
    }
    alpha(animatedProgress.value)
}

object ComposableUtils {

    val IMAGE_WIDTH @Composable get() = with(LocalDensity.current) { 360.toDp() }
    val IMAGE_HEIGHT @Composable get() = with(LocalDensity.current) { 480.toDp() }

}

@Composable
fun StaggeredVerticalGrid(
    modifier: Modifier = Modifier,
    maxColumnWidth: Dp,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeableXY: MutableMap<Placeable, Pair<Int, Int>> = mutableMapOf()

        check(constraints.hasBoundedWidth) {
            "Unbounded width not supported"
        }
        val columns = ceil(constraints.maxWidth / maxColumnWidth.toPx()).toInt()
        val columnWidth = constraints.maxWidth / columns
        val itemConstraints = constraints.copy(maxWidth = columnWidth)
        val colHeights = IntArray(columns) { 0 } // track each column's height
        val placeables = measurables.map { measurable ->
            val column = shortestColumn(colHeights)
            val placeable = measurable.measure(itemConstraints)
            placeableXY[placeable] = Pair(columnWidth * column, colHeights[column])
            colHeights[column] += placeable.height
            placeable
        }

        val height = colHeights.maxOrNull()
            ?.coerceIn(constraints.minHeight, constraints.maxHeight)
            ?: constraints.minHeight
        layout(
            width = constraints.maxWidth,
            height = height
        ) {
            placeables.forEach { placeable ->
                placeable.place(
                    x = placeableXY.getValue(placeable).first,
                    y = placeableXY.getValue(placeable).second
                )
            }
        }
    }
}

@Composable
fun StaggeredVerticalGrid(
    modifier: Modifier = Modifier,
    columns: Int,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeableXY: MutableMap<Placeable, Pair<Int, Int>> = mutableMapOf()

        check(constraints.hasBoundedWidth) {
            "Unbounded width not supported"
        }
        val columnWidth = constraints.maxWidth / columns
        val itemConstraints = constraints.copy(maxWidth = columnWidth)
        val colHeights = IntArray(columns) { 0 } // track each column's height
        val placeables = measurables.map { measurable ->
            val column = shortestColumn(colHeights)
            val placeable = measurable.measure(itemConstraints)
            placeableXY[placeable] = Pair(columnWidth * column, colHeights[column])
            colHeights[column] += placeable.height
            placeable
        }

        val height = colHeights.maxOrNull()
            ?.coerceIn(constraints.minHeight, constraints.maxHeight)
            ?: constraints.minHeight
        layout(
            width = constraints.maxWidth,
            height = height
        ) {
            placeables.forEach { placeable ->
                placeable.place(
                    x = placeableXY.getValue(placeable).first,
                    y = placeableXY.getValue(placeable).second
                )
            }
        }
    }
}

private fun shortestColumn(colHeights: IntArray): Int {
    var minHeight = Int.MAX_VALUE
    var column = 0
    colHeights.forEachIndexed { index, height ->
        if (height < minHeight) {
            minHeight = height
            column = index
        }
    }
    return column
}

fun Int.toComposeColor() = Color(this)

@Composable
fun CustomChip(
    category: String,
    modifier: Modifier = Modifier,
    textColor: Color? = MaterialTheme.colors.onSurface,
    backgroundColor: Color? = MaterialTheme.colors.surface
) {
    Surface(
        modifier = Modifier
            .padding(end = 8.dp, bottom = 8.dp)
            .then(modifier),
        elevation = 8.dp,
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor ?: MaterialTheme.colors.surface
    ) {
        Row {
            Text(
                text = category,
                style = MaterialTheme.typography.body2,
                color = textColor ?: MaterialTheme.colors.onSurface,
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.CenterVertically),
                textAlign = TextAlign.Center
            )
        }
    }
}

@ExperimentalAnimationApi
/**
 * @param state Use [updateAnimatedItemsState].
 */
inline fun <T> LazyListScope.animatedItems(
    state: List<AnimatedItem<T>>,
    enterTransition: EnterTransition = expandVertically(),
    exitTransition: ExitTransition = shrinkVertically(),
    noinline key: ((item: T) -> Any)? = null,
    crossinline itemContent: @Composable LazyItemScope.(item: T) -> Unit
) = animatedItemsIndexed(state, enterTransition, exitTransition, key) { _, item -> itemContent(item) }

@ExperimentalAnimationApi
/**
 * @param state Use [updateAnimatedItemsState].
 */
inline fun <T> LazyListScope.animatedItemsIndexed(
    state: List<AnimatedItem<T>>,
    enterTransition: EnterTransition = expandVertically(),
    exitTransition: ExitTransition = shrinkVertically(),
    noinline key: ((item: T) -> Any)? = null,
    crossinline itemContent: @Composable LazyItemScope.(index: Int, item: T) -> Unit
) {
    items(
        state.size,
        if (key != null) { keyIndex: Int -> key(state[keyIndex].item) } else null
    ) { index ->
        val item = state[index]
        val visibility = item.visibility

        key(key?.invoke(item.item)) {
            AnimatedVisibility(
                visibleState = visibility,
                enter = enterTransition,
                exit = exitTransition
            ) { itemContent(index, item.item) }
        }
    }
}

@Composable
fun <T> updateAnimatedItemsState(
    newList: List<T>
): State<List<AnimatedItem<T>>> {

    val state = remember { mutableStateOf(emptyList<AnimatedItem<T>>()) }
    LaunchedEffect(newList) {
        if (state.value == newList) {
            return@LaunchedEffect
        }
        val oldList = state.value.toList()

        val diffCb = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldList.size
            override fun getNewListSize(): Int = newList.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldList[oldItemPosition].item == newList[newItemPosition]

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldList[oldItemPosition].item == newList[newItemPosition]

        }
        val diffResult = calculateDiff(false, diffCb)
        val compositeList = oldList.toMutableList()

        diffResult.dispatchUpdatesTo(object : ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) {
                for (i in 0 until count) {
                    val newItem = AnimatedItem(visibility = MutableTransitionState(false), newList[position + i])
                    newItem.visibility.targetState = true
                    compositeList.add(position + i, newItem)
                }
            }

            override fun onRemoved(position: Int, count: Int) {
                for (i in 0 until count) {
                    compositeList[position + i].visibility.targetState = false
                }
            }

            override fun onMoved(fromPosition: Int, toPosition: Int) {
                // not detecting moves.
            }

            override fun onChanged(position: Int, count: Int, payload: Any?) {
                // irrelevant with compose.
            }
        })
        if (state.value != compositeList) {
            state.value = compositeList
        }
        val initialAnimation = Animatable(1.0f)
        initialAnimation.animateTo(0f)
        state.value = state.value.filter { it.visibility.targetState }
    }

    return state
}

data class AnimatedItem<T>(
    val visibility: MutableTransitionState<Boolean>,
    val item: T,
) {

    override fun hashCode(): Int {
        return item?.hashCode() ?: 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AnimatedItem<*>

        if (item != other.item) return false

        return true
    }
}

suspend fun calculateDiff(
    detectMoves: Boolean = true,
    diffCb: DiffUtil.Callback
): DiffUtil.DiffResult {
    return withContext(Dispatchers.Unconfined) {
        DiffUtil.calculateDiff(diffCb, detectMoves)
    }
}

@ExperimentalMaterialApi
@Composable
fun CoverCard(imageUrl: String, name: String, placeHolder: Int, error: Int = placeHolder, onClick: () -> Unit = {}) {

    val context = LocalContext.current

    Card(
        onClick = onClick,
        modifier = Modifier
            .padding(5.dp)
            .size(
                ComposableUtils.IMAGE_WIDTH,
                ComposableUtils.IMAGE_HEIGHT
            ),
        indication = rememberRipple(),
        onClickLabel = name,
    ) {

        Box {
            GlideImage(
                imageModel = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                requestBuilder = Glide.with(LocalView.current)
                    .asDrawable()
                    //.override(360, 480)
                    .placeholder(placeHolder)
                    .error(error)
                    .fallback(placeHolder)
                    .transform(RoundedCorners(5)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(ComposableUtils.IMAGE_WIDTH, ComposableUtils.IMAGE_HEIGHT),
                loading = {
                    Image(
                        bitmap = AppCompatResources.getDrawable(context, placeHolder)!!.toBitmap().asImageBitmap(),
                        contentDescription = name,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(ComposableUtils.IMAGE_WIDTH, ComposableUtils.IMAGE_HEIGHT)
                    )
                },
                failure = {
                    Image(
                        bitmap = AppCompatResources.getDrawable(context, error)!!.toBitmap().asImageBitmap(),
                        contentDescription = name,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(ComposableUtils.IMAGE_WIDTH, ComposableUtils.IMAGE_HEIGHT)
                    )
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black
                            ),
                            startY = 50f
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    name,
                    style = MaterialTheme
                        .typography
                        .body1
                        .copy(textAlign = TextAlign.Center, color = Color.White),
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                )
            }
        }

    }
}

@ExperimentalMaterialApi
@Composable
fun PlaceHolderCoverCard(placeHolder: Int) {
    Card(
        modifier = Modifier
            .padding(5.dp)
            .size(
                ComposableUtils.IMAGE_WIDTH,
                ComposableUtils.IMAGE_HEIGHT
            )
    ) {

        Box {
            Image(
                painter = painterResource(placeHolder),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .placeholder(true)
                    .size(ComposableUtils.IMAGE_WIDTH, ComposableUtils.IMAGE_HEIGHT)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black
                            ),
                            startY = 50f
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    "",
                    style = MaterialTheme
                        .typography
                        .body1
                        .copy(textAlign = TextAlign.Center, color = Color.White),
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .placeholder(true)
                        .align(Alignment.BottomCenter)
                )
            }
        }

    }
}

@ExperimentalMaterialApi
@Composable
fun <T> BottomSheetDeleteScaffold(
    listOfItems: List<T>,
    multipleTitle: String,
    onRemove: (T) -> Unit,
    onMultipleRemove: (SnapshotStateList<T>) -> Unit,
    customSingleRemoveDialog: (T) -> Boolean = { true },
    topBar: @Composable (() -> Unit)? = null,
    itemUi: @Composable (T) -> Unit,
    mainView: @Composable (PaddingValues) -> Unit
) {
    val state = rememberBottomSheetScaffoldState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    BottomSheetScaffold(
        scaffoldState = state,
        topBar = topBar,
        sheetShape = MaterialTheme.shapes.medium.copy(CornerSize(4.dp), CornerSize(4.dp), CornerSize(0.dp), CornerSize(0.dp)),
        sheetPeekHeight = ButtonDefaults.MinHeight + 4.dp,
        sheetContent = {

            val itemsToDelete = remember { mutableStateListOf<T>() }

            var showPopup by remember { mutableStateOf(false) }

            if (showPopup) {

                val onDismiss = { showPopup = false }

                AlertDialog(
                    onDismissRequest = onDismiss,
                    title = { Text(multipleTitle) },
                    text = { Text(context.resources.getQuantityString(R.plurals.areYouSureRemove, itemsToDelete.size, itemsToDelete.size)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onDismiss()
                                scope.launch { state.bottomSheetState.collapse() }
                                onMultipleRemove(itemsToDelete)
                            }
                        ) { Text(stringResource(R.string.yes)) }
                    },
                    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.no)) } }
                )

            }

            Scaffold(
                topBar = {
                    Button(
                        onClick = {
                            scope.launch {
                                if (state.bottomSheetState.isCollapsed) state.bottomSheetState.expand()
                                else state.bottomSheetState.collapse()
                                itemsToDelete.clear()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(ButtonDefaults.MinHeight + 4.dp),
                        shape = RoundedCornerShape(0f)
                    ) {
                        Text(
                            stringResource(R.string.delete_multiple),
                            style = MaterialTheme.typography.button
                        )
                    }
                },
                bottomBar = {
                    BottomAppBar(
                        contentPadding = PaddingValues(0.dp)//Modifier.padding(0.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch { state.bottomSheetState.collapse() }
                                itemsToDelete.clear()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 5.dp)
                        ) { Text(stringResource(id = R.string.cancel), style = MaterialTheme.typography.button) }

                        Button(
                            onClick = { showPopup = true },
                            enabled = itemsToDelete.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 5.dp)
                        ) { Text(stringResource(id = R.string.remove), style = MaterialTheme.typography.button) }
                    }
                }
            ) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    contentPadding = it,
                    modifier = Modifier.padding(5.dp)
                ) {
                    items(listOfItems) { i ->
                        DeleteItemView(
                            item = i,
                            deleteItemList = itemsToDelete,
                            customSingleRemoveDialog = customSingleRemoveDialog,
                            onRemove = onRemove,
                            itemUi = itemUi
                        )
                    }
                }
            }
        }
    ) { mainView(it) }
}

@ExperimentalMaterialApi
@Composable
private fun <T> DeleteItemView(
    item: T,
    deleteItemList: SnapshotStateList<T>,
    customSingleRemoveDialog: (T) -> Boolean,
    onRemove: (T) -> Unit,
    itemUi: @Composable (T) -> Unit
) {

    var showPopup by remember { mutableStateOf(false) }

    if (showPopup) {

        val onDismiss = { showPopup = false }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.remove)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismiss()
                        onRemove(item)
                    }
                ) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.no)) } }
        )

    }

    val dismissState = rememberDismissState(
        confirmStateChange = {
            if (it == DismissValue.DismissedToEnd || it == DismissValue.DismissedToStart) {
                if (customSingleRemoveDialog(item)) {
                    showPopup = true
                }
            }
            false
        }
    )

    SwipeToDismiss(
        state = dismissState,
        directions = setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart),
        dismissThresholds = { FractionalThreshold(0.5f) },
        background = {
            val direction = dismissState.dismissDirection ?: return@SwipeToDismiss
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    DismissValue.Default -> Color.Transparent
                    DismissValue.DismissedToEnd -> Color.Red
                    DismissValue.DismissedToStart -> Color.Red
                }
            )
            val alignment = when (direction) {
                DismissDirection.StartToEnd -> Alignment.CenterStart
                DismissDirection.EndToStart -> Alignment.CenterEnd
            }
            val icon = when (direction) {
                DismissDirection.StartToEnd -> Icons.Default.Delete
                DismissDirection.EndToStart -> Icons.Default.Delete
            }
            val scale by animateFloatAsState(if (dismissState.targetValue == DismissValue.Default) 0.75f else 1f)

            Box(
                Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.scale(scale)
                )
            }
        }
    ) {
        Card(
            elevation = 5.dp,
            modifier = Modifier.fillMaxSize(),
            interactionSource = MutableInteractionSource(),
            indication = rememberRipple(),
            onClick = { if (item in deleteItemList) deleteItemList.remove(item) else deleteItemList.add(item) },
            backgroundColor = animateColorAsState(if (item in deleteItemList) Color(0xfff44336) else MaterialTheme.colors.surface).value
        ) { itemUi(item) }
    }

}

@Composable
fun Modifier.scaleRotateOffset(
    canScale: Boolean = true,
    canRotate: Boolean = true,
    canOffset: Boolean = true
): Modifier {
    var scale by remember { mutableStateOf(1f) }
    var rotation by remember { mutableStateOf(0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, offsetChange, rotationChange ->
        if (canScale) scale *= zoomChange
        if (canRotate) rotation += rotationChange
        if (canOffset) offset += offsetChange
    }
    return graphicsLayer(
        scaleX = scale,
        scaleY = scale,
        rotationZ = rotation,
        translationX = offset.x,
        translationY = offset.y
    )
        // add transformable to listen to multitouch transformation events
        // after offset
        .transformable(state = state)
}

interface AutoCompleteEntity {
    fun filter(query: String): Boolean
}

private typealias ItemSelected<T> = (T) -> Unit

interface AutoCompleteScope<T : AutoCompleteEntity> : AutoCompleteDesignScope {
    var isSearching: Boolean
    fun filter(query: String)
    fun onItemSelected(block: ItemSelected<T> = {})
}

interface AutoCompleteDesignScope {
    var boxWidthPercentage: Float
    var shouldWrapContentHeight: Boolean
    var boxMaxHeight: Dp
    var boxBorderStroke: BorderStroke
    var boxShape: Shape
}

class AutoCompleteState<T : AutoCompleteEntity>(private val startItems: List<T>) : AutoCompleteScope<T> {
    private var onItemSelectedBlock: ItemSelected<T>? = null

    fun selectItem(item: T) {
        onItemSelectedBlock?.invoke(item)
    }

    private var filteredItems by mutableStateOf(startItems)
    override var isSearching by mutableStateOf(false)
    override var boxWidthPercentage by mutableStateOf(.9f)
    override var shouldWrapContentHeight by mutableStateOf(false)
    override var boxMaxHeight: Dp by mutableStateOf(TextFieldDefaults.MinHeight * 3)
    override var boxBorderStroke by mutableStateOf(BorderStroke(2.dp, Color.Black))
    override var boxShape: Shape by mutableStateOf(RoundedCornerShape(8.dp))

    override fun filter(query: String) {
        if (isSearching) filteredItems = startItems.filter { entity -> entity.filter(query) }
    }

    override fun onItemSelected(block: ItemSelected<T>) {
        onItemSelectedBlock = block
    }
}

interface ValueAutoCompleteEntity<T> : AutoCompleteEntity {
    val value: T
}

typealias CustomFilter<T> = (T, String) -> Boolean

fun <T> List<T>.asAutoCompleteEntities(filter: CustomFilter<T>): List<ValueAutoCompleteEntity<T>> {
    return map {
        object : ValueAutoCompleteEntity<T> {
            override val value: T = it

            override fun filter(query: String): Boolean {
                return filter(value, query)
            }
        }
    }
}

@ExperimentalAnimationApi
@Composable
fun <T : AutoCompleteEntity> AutoCompleteBox(
    items: List<T>,
    itemContent: @Composable (T) -> Unit,
    content: @Composable AutoCompleteScope<T>.() -> Unit
) {
    val autoCompleteState = remember { AutoCompleteState(startItems = items) }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        autoCompleteState.content()

        DropdownMenu(
            expanded = autoCompleteState.isSearching,
            onDismissRequest = { },
            modifier = Modifier.autoComplete(autoCompleteState),
            properties = PopupProperties(focusable = false)
        ) {
            items.forEach { item ->
                DropdownMenuItem(onClick = { autoCompleteState.selectItem(item) }) {
                    itemContent(item)
                }
            }
        }
    }
}

private fun Modifier.autoComplete(
    autoCompleteItemScope: AutoCompleteDesignScope
): Modifier = composed {
    val baseModifier = if (autoCompleteItemScope.shouldWrapContentHeight)
        wrapContentHeight()
    else
        heightIn(0.dp, autoCompleteItemScope.boxMaxHeight)

    baseModifier
        .fillMaxWidth(autoCompleteItemScope.boxWidthPercentage)
        .border(
            border = autoCompleteItemScope.boxBorderStroke,
            shape = autoCompleteItemScope.boxShape
        )
}
