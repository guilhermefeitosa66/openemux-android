package com.openemux.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.DpSize
import kotlinx.coroutines.delay
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.navigation.NavBackStackEntry
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.runtime.CompositionLocalProvider
import java.text.NumberFormat
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.coverAspect
import com.openemux.mobile.data.Meta
import com.openemux.mobile.data.Rom
import com.openemux.mobile.data.ShelfContent
import com.openemux.mobile.data.shelfContent
import com.retroarch.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen() {
    val a = LocalActions.current
    val roms by a.app.library.roms.collectAsState()
    val meta by a.app.library.meta.collectAsState()
    val loaded by a.app.library.loaded.collectAsState()
    val content = shelfContent(loaded, roms)
    val focusedShelf = rememberSaveable { mutableStateOf<String?>(null) }
    val lib = a.app.library
    val present = lib.orderedConsoles(meta.consoleOrder).filter { c -> roms.any { it.console == c } }
    // While a shelf is dragged: the order on screen, the dragged console and how far it is from its slot.
    var live by remember { mutableStateOf<List<Console>?>(null) }
    var dragged by remember { mutableStateOf<Console?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var menuFor by remember { mutableStateOf<Console?>(null) }
    val gridState = rememberLazyGridState()
    val haptic = LocalHapticFeedback.current
    GamepadButtons(
        hints = listOf(
            "A" to stringResource(R.string.library_hint_open), "Y" to stringResource(R.string.library_hint_order),
            "SELECT" to stringResource(R.string.library_search), "L1 R1" to stringResource(R.string.library_hint_tabs),
        ),
        select = { a.nav.navigate(Routes.library("SEARCH")) },
        y = { menuFor = Console.byId(focusedShelf.value) },
    )

    val favoritesShort = stringResource(R.string.library_favorites_eyebrow)
    val favoritesName = stringResource(R.string.library_favorites)
    val allShort = stringResource(R.string.library_all_eyebrow)
    val allName = stringResource(R.string.library_all_games)
    val allCompactName = stringResource(R.string.library_chip_all)

    // A tablet's shelf: a header row, the games to continue, more columns of taller cards and an import card.
    val spec = shelfSpecOf(LocalWindowLayout.current)
    // The newest files, only where the row shows: reading every file's date is not for the main thread.
    val added by produceState(emptyList<Rom>(), roms, spec.recentlyAdded) {
        value = if (spec.recentlyAdded) withContext(Dispatchers.IO) { recentlyAdded(roms, { it.file.lastModified() }, RECENTLY_ADDED_COUNT) } else emptyList()
    }

    // The games to continue, sorted once per library or play-time change rather than on every pass over the grid.
    val playing = remember(roms, meta, spec.continueCount) { continueList(roms, { meta.roms[it.key]?.lastPlayed ?: 0L }, spec.continueCount) }

    // In tabletop the shelves stay on the top half; the games to continue and the way to add more are on the bottom one.
    val tabletop = LocalTabletop.current
    if (tabletop) TabletopDeck {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)) {
            if (spec.tablet && playing.isNotEmpty()) {
                ContinueRow(playing, spec.continueCount, spec, meta, focusedShelf, Modifier.padding(bottom = 16.dp))
            }
            SecondaryButton(stringResource(R.string.library_shelf_empty_action), { a.nav.navigateTab(Routes.IMPORT) }, Modifier.fillMaxWidth(), icon = Icons.Download)
        }
    }

    // As many cards side by side as the shelf's own width holds: one on a phone in portrait, two in landscape, three on a wide tablet.
    // The focused shelf keeps clear of the top and of the bars over the bottom.
    MarginFocusScroll(trailing = FocusMargin + TabBarHeight + hintBarInset) { BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
        val columns = shelfColumns(maxWidth.value, spec.maxColumns, spec.sidePadding, spec.gap)
        val cardSpec = shelfCardSpec(spec.tablet)
        LazyVerticalGrid(
            GridCells.Fixed(columns),
            Modifier.fillMaxSize().holdFocusAtEnd(FocusDirection.Down),
            state = gridState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = spec.sidePadding.dp),
            horizontalArrangement = Arrangement.spacedBy(spec.gap.dp),
        ) {
            if (spec.tablet) item(span = { GridItemSpan(maxLineSpan) }) {
                TabletShelfHeader(spec, roms, content != ShelfContent.Loading)
            } else item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.bleed(spec.sidePadding.dp)) {
                    // The grid clips at its top: the room above the header is the focus ring's.
                    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = FocusOutset), verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.oe_logo), contentDescription = null, modifier = Modifier.height(34.dp))
                        // On a narrow screen the credit wraps under the name instead of pushing the button.
                        FlowRow(
                            Modifier.padding(start = 10.dp).weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("OpenEmux", style = Type.Title.copy(fontSize = 18.sp))
                            PoweredByRetroArch()
                        }
                        RoundIconButton(Icons.Search, stringResource(R.string.library_search_all_consoles), { a.nav.navigate(Routes.library("SEARCH")) })
                    }
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp)) {
                        Text(stringResource(R.string.library_shelf_title), style = Type.Display)
                        val consoles = Console.entries.count { c -> roms.any { it.console == c } }
                        // No count until the library is known: "0 consoles" reads as a lost library.
                        if (content != ShelfContent.Loading) Text("${countString(R.plurals.library_consoles_count, consoles, consoles)} · ${countString(R.plurals.library_games_count, roms.size, roms.size)}", style = Type.Small.copy(fontSize = 14.sp), modifier = Modifier.padding(top = 6.dp))
                    }
                    UpdateBanner(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp))
                    ArtworkSyncPill(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp))
                }
            }
            if (content == ShelfContent.Empty) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        stringResource(R.string.library_shelf_empty_title),
                        stringResource(R.string.library_shelf_empty_body),
                        stringResource(R.string.library_shelf_empty_action),
                        { a.nav.navigate(Routes.IMPORT) },
                        Modifier.bleed(spec.sidePadding.dp),
                    )
                }
            } else if (content == ShelfContent.Shelves) {
                if (spec.tablet) {
                    // No more continue cards than the grid below has columns, so the two line up.
                    if (playing.isNotEmpty() && !tabletop) item(span = { GridItemSpan(maxLineSpan) }) {
                        ContinueRow(playing, minOf(spec.continueCount, columns), spec, meta, focusedShelf, Modifier.padding(top = 28.dp))
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(Modifier.padding(top = 28.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.library_consoles_heading), style = Type.Section.copy(fontSize = 17.sp), modifier = Modifier.weight(1f))
                            // A gamepad reorders with Y, not with a long press.
                            if (!InputMode.gamepad) Text(stringResource(R.string.library_reorder_hint), style = Type.Small)
                        }
                    }
                }
                val favorites = roms.filter { meta.roms[it.key]?.favorite == true }
                val shown = live ?: present
                // All and Favorites come first; they are no consoles, so they never move nor take a dragged card.
                val pinned = pinnedShelves(roms.size, favorites.size)
                val pair = pinnedAsPair(columns, pinned)
                val first = pinned.firstOrNull() ?: shown.firstOrNull()?.id
                if (pair) item(key = "pinned", span = { GridItemSpan(maxLineSpan) }) {
                    // One column: the two share the row, so the pair is no taller than one card.
                    Row(Modifier.padding(vertical = (spec.gap / 2).dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(spec.gap.dp)) {
                        listOf(Triple(allShort, allCompactName, roms) to "ALL", Triple(favoritesShort, favoritesName, favorites) to "FAV").forEach { (shelf, filter) ->
                            val (short, name, list) = shelf
                            val (background, ink) = pinnedLook(filter, Oe.dark)
                            CompactShelfCard(
                                if (filter == "ALL") Icons.Grid else Icons.Star, if (filter == "ALL") ink else Oe.Star,
                                short, name, countString(R.plurals.library_games_count, list.size, list.size), background, ink,
                                spec.cardHeight.dp, Modifier.weight(1f).fillMaxHeight(), Modifier.restorableFocus(filter, focusedShelf, initial = first == filter),
                            ) { a.nav.navigate(Routes.library(filter)) }
                        }
                    }
                }
                val shelves = buildList {
                    if (!pair) pinned.forEach { f ->
                        add((if (f == "ALL") Triple(allShort, allName, roms) else Triple(favoritesShort, favoritesName, favorites)) to f)
                    }
                    shown.forEach { c -> add(Triple(c.short, c.displayName, roms.filter { it.console == c }) to c.id) }
                }
                gridItems(shelves, key = { it.second }) { (shelf, filter) ->
                    val (short, name, list) = shelf
                    val console = Console.byId(filter)
                    val (background, ink) = if (console == null) pinnedLook(filter, Oe.dark) else console.shelf(Oe.dark).let { (back, text) -> SolidColor(back) to text }
                    val isDragged = console != null && console == dragged
                    val moveUp = stringResource(R.string.library_shelf_move_up)
                    val moveDown = stringResource(R.string.library_shelf_move_down)
                    // No fade-in: a card that arrives (the library restored, a scan, an import) is drawn at once; reordering still slides.
                    ShelfCard(
                        console, filter == "ALL", short, name, countString(R.plurals.library_games_count, list.size, list.size), background, ink,
                        cardSpec, spec.cardHeight.dp, (spec.gap / 2).dp, if (spec.tablet) 24.dp else 22.dp,
                        Modifier
                            .then(if (isDragged) Modifier.zIndex(2f).graphicsLayer { translationX = dragOffset.x; translationY = dragOffset.y; scaleX = 1.03f; scaleY = 1.03f } else Modifier.animateItem(fadeInSpec = null))
                            .restorableFocus(filter, focusedShelf, initial = first == filter)
                            .then(
                                if (console == null) Modifier else Modifier
                                    .semantics {
                                        customActions = listOf(
                                            CustomAccessibilityAction(moveUp) { lib.moveConsole(console, present, -1) },
                                            CustomAccessibilityAction(moveDown) { lib.moveConsole(console, present, 1) },
                                        )
                                    }
                                    .reorderGestures(
                                        onStart = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            live = present; dragged = console; dragOffset = Offset.Zero
                                        },
                                        onDrag = { delta ->
                                            // One column: only the vertical move means anything, so a sideways drift never stalls the reorder.
                                            dragOffset += if (columns == 1) Offset(0f, delta.y) else delta
                                            val items = gridState.layoutInfo.visibleItemsInfo
                                            val self = items.firstOrNull { it.key == console.id }
                                            if (self != null) {
                                                // The card under the dragged card's centre, in either column.
                                                val key = reorderTarget(
                                                    self.offset.x + self.size.width / 2 + dragOffset.x,
                                                    self.offset.y + self.size.height / 2 + dragOffset.y,
                                                    console.id,
                                                    items.map { Slot(it.key, it.offset.x.toFloat(), it.offset.y.toFloat(), it.size.width.toFloat(), it.size.height.toFloat()) },
                                                ) { k -> live?.any { it.id == k } == true }
                                                val target = items.firstOrNull { it.key == key }
                                                if (target != null) {
                                                    // Previous/next in reading order: left to right, then down.
                                                    val order = live!!.toMutableList()
                                                    val to = order.indexOfFirst { it.id == target.key }
                                                    order.remove(console)
                                                    order.add(to, console)
                                                    live = order
                                                    // The card is laid out in its new slot: keep it under the finger.
                                                    dragOffset += Offset((self.offset.x - target.offset.x).toFloat(), (self.offset.y - target.offset.y).toFloat())
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            }
                                        },
                                        onEnd = { moved ->
                                            val order = live
                                            // A drag that ends where it began leaves the stored order (and "Reset order") untouched.
                                            // The shelves on screen may have changed mid-drag (a scan, a removal): then the drag order no longer maps onto them.
                                            if (moved && order != null && order != present && order.toSet() == present.toSet()) {
                                                // Only the shelves on screen moved; the others keep their slots in the full order.
                                                val it = order.iterator()
                                                lib.setConsoleOrder(lib.orderedConsoles().map { c -> if (c in present) it.next() else c })
                                            } else if (!moved) {
                                                menuFor = console
                                            }
                                            live = null; dragged = null; dragOffset = Offset.Zero
                                        },
                                    )
                            ),
                    ) {
                        a.nav.navigate(Routes.library(filter))
                    }
                }
                if (spec.tablet) {
                    // Never moves and is never a place to drop a dragged card: its key is no console's.
                    item(key = "import-more") { ImportMoreCard(spec.cardHeight.dp, (spec.gap / 2).dp, Modifier.restorableFocus("import-more", focusedShelf)) }
                    if (added.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.padding(top = 22.dp)) {
                            SectionHeading(stringResource(R.string.library_recently_added), stringResource(R.string.library_see_all)) { a.nav.navigate(Routes.library("ALL")) }
                            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(spec.gap.dp)) {
                                added.forEach { rom -> AddedCartridge(rom, Modifier.weight(1f), Modifier.restorableFocus("added-${rom.key}", focusedShelf)) }
                                repeat(RECENTLY_ADDED_COUNT - added.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            // On a tablet the logo row is the rail's, so the engine credit closes the page instead.
            if (spec.tablet) item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) { PoweredByRetroArch() }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(BottomSpace) }
        }
    } }

    menuFor?.let { console ->
        val i = present.indexOf(console)
        OeSheet({ menuFor = null }) {
            SheetBarIcons()
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text(console.displayName, style = Type.Title.copy(fontSize = 22.sp))
                Text(stringResource(R.string.library_shelf_order_tip), style = Type.Small, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                Card {
                    // Only the moves that have somewhere to go, as the desktop sidebar does.
                    var divider = false
                    val games = roms.filter { it.console == console }
                    if (games.isNotEmpty()) {
                        ListRow(
                            stringResource(R.string.library_share_console), value = countString(R.plurals.share_games, games.size, games.size),
                            leading = { Icon(Icons.Share, Oe.AccentText, 20.dp) },
                            onClick = {
                                menuFor = null
                                a.app.shareCoordinator.startSend(games)
                                a.nav.navigate(Routes.SHARE)
                            },
                        )
                        divider = true
                    }
                    if (i > 0) {
                        ListRow(stringResource(R.string.library_shelf_move_up), onClick = { lib.moveConsole(console, present, -1); menuFor = null })
                        divider = true
                    }
                    if (i in 0 until present.lastIndex) {
                        ListRow(stringResource(R.string.library_shelf_move_down), divider = divider, onClick = { lib.moveConsole(console, present, 1); menuFor = null })
                        divider = true
                    }
                    if (meta.consoleOrder.isNotEmpty()) {
                        ListRow(stringResource(R.string.library_shelf_reset_order), divider = divider, onClick = { lib.resetConsoleOrder(); menuFor = null })
                    }
                }
            }
        }
    }
}

/**
 * Long press, then drag: reorders. Long press and let go: [onEnd] with false.
 * Watches the Initial pass so that, once the press is long, the card's click
 * and the list's scroll never see the gesture.
 */
private fun Modifier.reorderGestures(onStart: () -> Unit, onDrag: (Offset) -> Unit, onEnd: (moved: Boolean) -> Unit): Modifier = composed {
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                }
                true
            }
            if (released != null) return@awaitEachGesture
            start()
            // The farthest the finger got from where the press turned long: a still finger's jitter never adds up to a drag.
            var origin: Offset? = null
            var travel = 0f
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                change.consume()
                if (!change.pressed) break
                val from = origin ?: change.previousPosition.also { origin = it }
                travel = maxOf(travel, (change.position - from).getDistance())
                val delta = change.position - change.previousPosition
                if (delta != Offset.Zero) drag(delta)
            }
            end(travel > viewConfiguration.touchSlop)
        }
    }
}

/** A console's shelf ([console] null: All when [all], else Favorites): its icon, large and straight on the card's colour, then tag, name and count. */
@Composable
private fun ShelfCard(
    console: Console?, all: Boolean, short: String, name: String, count: String, background: Brush, ink: Color, card: ShelfCardSpec,
    height: Dp, spacing: Dp, corner: Dp, focus: Modifier, onClick: () -> Unit,
) {
    Box(
        Modifier
            .padding(vertical = spacing)
            .fillMaxWidth()
            // At least [height]: a large font scale grows the card instead of clipping the count.
            .heightIn(min = height)
            .gamepadFocus(corner = corner, scale = 1.02f)
            .then(focus)
            .clip(RoundedCornerShape(corner))
            .background(background)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        val tag = Type.Tag.copy(color = ink.copy(alpha = 0.72f), fontSize = 12.sp, letterSpacing = 1.sp)
        Row(Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(horizontal = card.startPadding.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ShelfIconTile(console, all, ink, card.box.dp, card.icon.dp, card.star.dp)
            Column(Modifier.padding(start = 16.dp)) {
                Text(short, style = tag, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(name, style = Type.Title.copy(fontSize = card.nameSp.sp, lineHeight = (card.nameSp + 3).sp, color = ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(count, style = Type.Small.copy(color = ink.copy(alpha = 0.78f)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** The tablet shelf's top: title and counts, then search and import on the same row. */
@Composable
private fun TabletShelfHeader(spec: ShelfSpec, roms: List<Rom>, known: Boolean) {
    val a = LocalActions.current
    Column(Modifier.padding(top = spec.topPadding.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.library_shelf_title), style = Type.Display.copy(fontSize = 36.sp, lineHeight = 40.sp))
                val consoles = Console.entries.count { c -> roms.any { it.console == c } }
                // As on the phone header: no count while the library is not known yet.
                if (known) Text("${countString(R.plurals.library_consoles_count, consoles, consoles)} · ${countString(R.plurals.library_games_count, roms.size, roms.size)}", style = Type.Small.copy(fontSize = 14.sp), modifier = Modifier.padding(top = 4.dp))
            }
            val search = stringResource(R.string.library_search_all_consoles)
            if (spec.maxColumns > 2) {
                // Looks like a field, opens the one search page there is.
                Row(
                    Modifier.padding(start = 16.dp).width(340.dp).height(48.dp).gamepadFocus(corner = 24.dp).clip(RoundedCornerShape(24.dp)).background(Oe.Control)
                        .clickable(role = Role.Button) { a.nav.navigate(Routes.library("SEARCH")) }.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Search, Oe.Muted, 18.dp)
                    Text(search, style = Type.Body.copy(color = Oe.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
                }
            } else {
                Box(Modifier.padding(start = 12.dp)) { RoundIconButton(Icons.Search, search, { a.nav.navigate(Routes.library("SEARCH")) }) }
            }
            PrimaryButton(
                stringResource(R.string.library_shelf_empty_action), { a.nav.navigateTab(Routes.IMPORT) },
                Modifier.padding(start = 12.dp), icon = Icons.Download, height = 48.dp, sidePadding = 20.dp,
            )
        }
        UpdateBanner(Modifier.padding(top = 16.dp))
        ArtworkSyncPill(Modifier.padding(top = 16.dp))
    }
}

/** The tablet shelf's "Continue playing": a heading and [slots] cards side by side. */
@Composable
private fun ContinueRow(playing: List<Rom>, slots: Int, spec: ShelfSpec, meta: Meta, focusedShelf: MutableState<String?>, modifier: Modifier) {
    val a = LocalActions.current
    Column(modifier) {
        SectionHeading(stringResource(R.string.library_continue_playing), stringResource(R.string.library_see_recent)) { a.nav.navigateTab(Routes.RECENT) }
        val shown = playing.take(slots)
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(spec.gap.dp)) {
            shown.forEach { rom ->
                ContinueCard(
                    rom, meta.roms[rom.key]?.lastPlayed ?: 0L, if (spec.maxColumns > 2) 104.dp else 100.dp,
                    Modifier.weight(1f), Modifier.restorableFocus("continue-${rom.key}", focusedShelf),
                )
            }
            // Fewer games than slots: the cards keep their width.
            repeat(slots - shown.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** A section's title with its link on the right. */
@Composable
private fun SectionHeading(title: String, link: String, onLink: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Type.Section.copy(fontSize = 17.sp), modifier = Modifier.weight(1f))
        Text(
            link, style = Type.BodyMedium.copy(color = Oe.AccentText, fontSize = 14.sp),
            modifier = Modifier.gamepadFocus(corner = 8.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onLink).linkTarget().padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}

/** A game to pick up again: the whole card resumes it, as the play button does on Recent. */
@Composable
private fun ContinueCard(rom: Rom, lastPlayed: Long, height: Dp, modifier: Modifier, focus: Modifier) {
    val a = LocalActions.current
    val lib = a.app.library
    var lit by remember { mutableStateOf(false) }
    Row(
        modifier.height(height).gamepadFocus(corner = 20.dp, scale = 1.02f) { lit = it }.then(focus).clip(RoundedCornerShape(20.dp)).background(Oe.Surface)
            .clickable(onClickLabel = stringResource(R.string.library_continue_game, rom.title), role = Role.Button) { a.play(rom, true, null) }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tall shells are sized by the card's height, so they stay inside it.
        val cart = minOf(112.dp, (height - 24.dp) * rom.console.cartAspect)
        Cartridge(rom.console, labelFor(rom), rom.title, lib.effectiveColor(rom), Modifier.width(cart), pending = labelPending(rom), cover = coverFor(rom))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            GameName(rom.title, style = Type.BodyMedium.copy(fontWeight = FontWeight.SemiBold), focused = lit)
            Text("${rom.console.short} · ${formatAgo(lastPlayed)}", style = Type.Small, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        }
        Box(Modifier.size(44.dp).clip(CircleShape).background(Oe.Accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Play, Color.White, 18.dp, filled = true)
        }
    }
}

/** The last cell of the tablet shelf: a dashed card that opens Import. */
@Composable
private fun ImportMoreCard(height: Dp, spacing: Dp, focus: Modifier) {
    val a = LocalActions.current
    val dash = Oe.Border
    Box(
        Modifier.padding(vertical = spacing).fillMaxWidth().height(height).gamepadFocus(corner = 24.dp, scale = 1.02f).then(focus).clip(RoundedCornerShape(24.dp))
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    dash, topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            }
            .clickable(role = Role.Button) { a.nav.navigateTab(Routes.IMPORT) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Plus, Oe.Muted, 28.dp)
            Text(stringResource(R.string.library_import_more), style = Type.BodyMedium.copy(color = Oe.Muted), modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** One of the games that came in last, with its title; opens its page. */
@Composable
private fun AddedCartridge(rom: Rom, modifier: Modifier, focus: Modifier) {
    val a = LocalActions.current
    var lit by remember { mutableStateOf(false) }
    Column(
        modifier.gamepadFocus(corner = 10.dp, scale = 1.04f) { lit = it }.then(focus).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { a.nav.navigate(Routes.rom(rom.key)) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CappedCell(GridSpec.Carts) {
            Cartridge(rom.console, labelFor(rom), rom.title, a.app.library.effectiveColor(rom), it, pending = labelPending(rom), cover = coverFor(rom))
        }
        GameName(
            rom.title, style = Type.Caption.copy(color = Oe.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center, focused = lit,
            modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
        )
    }
}

/** The console's pixel icon (the grid in [ink] for All, the star for Favorites) in a [box] of room: no fill, the card's colour shows behind it. */
@Composable
private fun ShelfIconTile(console: Console?, all: Boolean, ink: Color, box: Dp, icon: Dp, star: Dp) {
    Box(Modifier.size(box), contentAlignment = Alignment.Center) {
        when {
            console != null -> ConsoleIcon(console, icon)
            all -> Icon(Icons.Grid, ink, star, filled = true)
            else -> Icon(Icons.Star, Oe.Star, star, filled = true)
        }
    }
}

/** A pinned shelf's colours, background and ink: blue for "ALL", purple for "FAV" (issue 655). */
private fun pinnedLook(filter: String, dark: Boolean): Pair<Brush, Color> =
    if (filter == "ALL") {
        if (dark) Brush.linearGradient(listOf(Color(0xFF2F5D8A), Color(0xFF1E3A5A))) to Color.White
        else Brush.linearGradient(listOf(Color(0xFFD3E3F5), Color(0xFFBCD3EE))) to Color(0xFF12304F)
    } else {
        if (dark) Brush.linearGradient(listOf(Color(0xFF5B3FA8), Color(0xFF3A2A6E))) to Color.White
        else Brush.linearGradient(listOf(Color(0xFFDDD3F8), Color(0xFFC6B8F0))) to Color(0xFF2E1F66)
    }

/**
 * Half of the one-column shelf's first row (All or Favorites): tag and icon on top, name and count
 * at the bottom. At least [height] tall; a large font scale grows it instead of clipping.
 */
@Composable
private fun CompactShelfCard(
    icon: String, iconColor: Color, short: String, name: String, count: String, background: Brush, ink: Color,
    height: Dp, modifier: Modifier, focus: Modifier, onClick: () -> Unit,
) {
    Column(
        modifier
            .heightIn(min = height)
            .gamepadFocus(corner = 22.dp, scale = 1.02f)
            .then(focus)
            .clip(RoundedCornerShape(22.dp))
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                short, style = Type.Tag.copy(color = ink.copy(alpha = 0.72f), fontSize = 11.sp, letterSpacing = 1.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Icon(icon, iconColor, 30.dp, filled = true, modifier = Modifier.padding(start = 8.dp))
        }
        Column(Modifier.padding(top = 8.dp)) {
            Text(name, style = Type.Title.copy(fontSize = 18.sp, color = ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(count, style = Type.Small.copy(color = ink.copy(alpha = 0.78f)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

private enum class ViewMode { Carts, Covers, List }
private enum class SortMode(@StringRes val label: Int) { Az(R.string.library_sort_az), Recent(R.string.library_sort_recent) }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(filter: String, entry: NavBackStackEntry? = null) {
    val a = LocalActions.current
    val lib = a.app.library
    val roms by lib.roms.collectAsState()
    val meta by lib.meta.collectAsState()
    val layout = LocalWindowLayout.current
    // On a wide tablet the picked game's detail sits beside the library; a tap picks, a second one opens it.
    // In tabletop the halves take the page instead.
    val pane = libraryPaneShown()
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    // A pick handed over (by a screen of its own, or left here on the way to one) is kept only beside its pane:
    // back on a phone the game was already shown on its own, so it must not be opened a second time.
    // Declared before OnSplitChange, so it runs first when this screen comes back.
    LaunchedEffect(entry) { entry?.savedStateHandle?.remove<String>(PICKED)?.let { picked = if (pane) it else null } }
    OnSplitChange(pane) { change ->
        if (change == SplitChange.Parted) picked?.let { picked = null; a.nav.navigate(Routes.rom(it)) }
    }
    // Opens a game on its own screen; from the pane, the pick waits in the saved state for the way back.
    fun openFull(key: String) {
        if (pane) picked?.let { entry?.savedStateHandle?.set(PICKED, it) }
        a.nav.navigate(Routes.rom(key))
    }
    val loaded by lib.loaded.collectAsState()
    // The empty texts wait until the library is known, as on the shelf.
    val known = shelfContent(loaded, roms) != ShelfContent.Loading
    val searchRequested = filter == "SEARCH"
    var mode by rememberSaveable { mutableStateOf(ViewMode.Carts) }
    var sort by rememberSaveable { mutableStateOf(SortMode.Az) }
    var searching by rememberSaveable { mutableStateOf(searchRequested) }
    var query by rememberSaveable { mutableStateOf("") }
    var current by rememberSaveable { mutableStateOf(if (searchRequested) "ALL" else filter) }

    val console = Console.byId(current)
    val list = roms
        .filter {
            when (current) {
                "FAV" -> meta.roms[it.key]?.favorite == true
                "ALL" -> true
                else -> it.console.id == current
            }
        }
        .filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
        .let { l -> if (sort == SortMode.Recent) l.sortedByDescending { meta.roms[it.key]?.lastPlayed ?: 0 } else l }
    // "All", favorites and search come grouped by console, in the user's order (desktop issue #384);
    // a single console needs no header. The page's sort holds inside each group.
    val groups: List<Pair<Console?, List<Rom>>> =
        if (console != null) listOf(null to list)
        else lib.orderedConsoles(meta.consoleOrder).mapNotNull { c -> list.filter { it.console == c }.takeIf { it.isNotEmpty() }?.let { c to it } }
    val firstKey = groups.firstOrNull()?.second?.firstOrNull()?.key
    val focusedRom = rememberSaveable { mutableStateOf<String?>(null) }
    // A focused game that left the library (a bulk remove, a remove from its page, a rescan) is
    // forgotten once it is gone, so the first tile that stayed takes the gamepad's focus. Not on
    // an empty list: that is also the library before its first scan, with the saved focus still good.
    LaunchedEffect(roms) { if (roms.isNotEmpty()) focusedRom.value = focusAfterRemoval(focusedRom.value, roms.map { it.key }) }
    val chipIds = listOf("ALL", "FAV") + lib.orderedConsoles(meta.consoleOrder).filter { c -> roms.any { it.console == c } }.map { it.id }
    fun chip(step: Int) {
        current = stepFilter(chipIds, current, step)
        focusedRom.value = null
    }
    fun pickFilter(id: String) {
        current = id
        focusedRom.value = null
    }
    val tabletop = LocalTabletop.current
    // Tabletop decides first: the top half takes the upright layout, however short it is.
    // On a short window the header holds the filter pager, whose steps show the shoulders themselves (issue 669).
    val landscapeRow = layout.short && !tabletop && mode != ViewMode.List
    // A phone held upright zooms the grid in steps (issue 868): each view keeps the step it was left at.
    val zoom = zoomApplies(layout, tabletop)
    val context = LocalContext.current
    val listSize = if (zoom) LibraryZoomPref.list else ListSize.Normal
    // A console without cartridge art shows its covers even in the cartridge view; the pick itself stays.
    val shown = if (mode == ViewMode.Carts && console != null) groupView(console) else mode
    var zoomMoving by remember { mutableStateOf(false) }
    var zoomAnchor by remember { mutableStateOf<ZoomAnchor?>(null) }
    var pill by remember { mutableStateOf<ZoomPill?>(null) }
    // Sets [view]'s step; false when it was already there. Read from the stored steps, never from a
    // composition: the pinch's handler can run against an older one.
    fun setZoom(view: ViewMode, value: Int): Boolean {
        when (view) {
            ViewMode.Carts -> if (value == LibraryZoomPref.carts) return false else LibraryZoomPref.setCarts(context, value)
            ViewMode.Covers -> if (value == LibraryZoomPref.covers) return false else LibraryZoomPref.setCovers(context, value)
            ViewMode.List -> if (value == LibraryZoomPref.list.ordinal) return false else LibraryZoomPref.setList(context, ListSize.entries[value])
        }
        pill = ZoomPill(view, value)
        return true
    }
    fun zoomStep(view: ViewMode): Int = when (view) {
        ViewMode.Carts -> LibraryZoomPref.carts
        ViewMode.Covers -> LibraryZoomPref.covers
        ViewMode.List -> LibraryZoomPref.list.ordinal
    }
    // Selection mode (issue #585): it only ever holds games on screen, so a hidden one is never removed.
    var selection by remember { mutableStateOf(RomSelection()) }
    var confirmRemove by remember { mutableStateOf(false) }
    val visibleKeys = remember(list) { list.map { it.key } }
    LaunchedEffect(visibleKeys) { selection = selection.retain(visibleKeys) }
    // Nothing left to remove (the filter changed under the dialog): close it rather than ask about 0 games.
    LaunchedEffect(selection.active) { if (!selection.active) confirmRemove = false }
    val haptic = LocalHapticFeedback.current
    val pick = TilePick(
        selection = selection,
        select = stringResource(R.string.library_select_tile),
        deselect = stringResource(R.string.library_deselect_tile),
        onStart = { key -> haptic.performHapticFeedback(HapticFeedbackType.LongPress); selection = selection.start(key) },
        onToggle = { key -> selection = selection.toggle(key) },
    )
    val scope = rememberCoroutineScope()
    // The game whose options Y opened, and the one waiting for its removal to be confirmed.
    var optionsFor by remember { mutableStateOf<String?>(null) }
    var removeKey by remember { mutableStateOf<String?>(null) }
    // The game holding the pad's focus: A opens it; null when another stop has it, where A just chooses.
    // Empty until focus first moves, so the bar opens on "Details" (the screen's first game is about to take it).
    val heldGame = remember { mutableStateOf<String?>("") }
    // A focused tile that leaves (a filter change, a remove) may not report losing focus: drop it with the list.
    LaunchedEffect(visibleKeys) { if (heldGame.value?.let { it.isNotEmpty() && it !in visibleKeys } == true) heldGame.value = "" }
    GamepadButtons(
        hints = listOf(
            "A" to stringResource(if (heldGame.value != null) R.string.library_hint_detail else R.string.settings_hint_choose),
            "X" to stringResource(R.string.library_hint_favorite),
            "Y" to stringResource(R.string.library_hint_options), "L1 R1" to stringResource(R.string.library_hint_console),
            "START" to stringResource(R.string.library_hint_select), "B" to stringResource(R.string.library_shelf_title),
        )
        // The pager's steps show the shoulders; while the search field has its place, the hint still does.
        .filter { !landscapeRow || searching || it.first != "L1 R1" },
        l1 = { chip(-1) },
        r1 = { chip(1) },
        // X, Y and START act on the game holding focus now, never on one remembered while focus is on
        // Sort or the view toggle. Y is that game's options, never a second A (issue 701).
        x = { heldGame.value?.takeIf { it in visibleKeys }?.let { key -> lib.updateRom(key) { it.copy(favorite = !it.favorite) } } },
        y = { heldGame.value?.takeIf { it in visibleKeys }?.let { optionsFor = it } },
        select = { searching = !searching; if (!searching) query = "" },
        start = { heldGame.value?.takeIf { it in visibleKeys }?.let { selection = selection.start(it) } },
    )
    // Share nearby (#591): the selected games, as the list shows them.
    fun shareSelection() {
        val picked = list.filter { it.key in selection }
        if (picked.isEmpty()) return
        selection = selection.clear()
        a.app.shareCoordinator.startSend(picked)
        a.nav.navigate(Routes.SHARE)
    }
    if (selection.active) {
        // On top of the screen's buttons while selecting; A stays the tile's click, which now toggles.
        GamepadButtons(
            hints = listOf(
                "A" to stringResource(R.string.library_hint_select), "X" to stringResource(R.string.library_hint_select_all),
                "Y" to stringResource(R.string.library_hint_remove), "SELECT" to stringResource(R.string.library_hint_share),
                "B" to stringResource(R.string.library_hint_done),
            ),
            select = ::shareSelection,
            x = { selection = selection.selectAll(visibleKeys) },
            y = { if (selection.count(visibleKeys) > 0) confirmRemove = true },
            start = { selection = selection.clear() },
        )
    }
    if (confirmRemove) {
        // The dialog is modal for the gamepad too: X, Start, L1/R1 and Select would otherwise
        // reach the selection (or the filter) behind it and change which games get removed.
        ModalPadButtons()
        val doomed = list.filter { it.key in selection }
        val n = doomed.size
        OeConfirmDialog(
            { confirmRemove = false },
            countString(R.plurals.library_remove_many_title, n, n), countString(R.plurals.library_remove_many_message, n),
            stringResource(R.string.detail_remove),
            {
                confirmRemove = false
                selection = selection.clear()
                scope.launch(Dispatchers.IO) { lib.deleteAll(doomed) }
            },
            stringResource(R.string.detail_cancel),
        )
    }
    // Y's options for one game, the detail's three-dot menu without leaving the list.
    optionsFor?.let { k -> roms.firstOrNull { it.key == k } }?.let { rom ->
        // Modal for the gamepad: X, L1/R1, Select and Start would act on the list behind the sheet.
        ModalPadButtons()
        GameOptionsSheet(
            rom,
            onDismiss = { optionsFor = null },
            onShare = {
                optionsFor = null
                a.app.shareCoordinator.startSend(listOf(rom))
                a.nav.navigate(Routes.SHARE)
            },
            onRemove = { optionsFor = null; removeKey = rom.key },
        )
    }
    removeKey?.let { k -> roms.firstOrNull { it.key == k } }?.let { rom ->
        // Modal for the gamepad too, as the bulk remove: the buttons behind it would act on the list.
        ModalPadButtons()
        OeConfirmDialog(
            { removeKey = null },
            stringResource(R.string.detail_remove_title, rom.title), stringResource(R.string.detail_remove_message),
            stringResource(R.string.detail_remove),
            {
                removeKey = null
                scope.launch(Dispatchers.IO) { lib.delete(rom) }
            },
            stringResource(R.string.detail_cancel),
        )
    }
    val bar: @Composable BoxScope.() -> Unit = {
        // Here, so it is composed after the split's own Back: while selecting, Back ends the selection first.
        BackHandler(enabled = selection.active) { selection = selection.clear() }
        AnimatedVisibility(selection.active, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            SelectionBar(
                count = selection.count(visibleKeys),
                allSelected = selection.allSelected(visibleKeys),
                onClose = { selection = selection.clear() },
                onSelectAll = { selection = selection.selectAll(visibleKeys) },
                onShare = ::shareSelection,
                onRemove = { if (selection.count(visibleKeys) > 0) confirmRemove = true },
            )
        }
    }

    if (landscapeRow) Box(Modifier.fillMaxSize()) {
        // Games per filter, for the pager's menu; the search does not narrow them.
        val counts = remember(roms, meta, chipIds) {
            chipIds.associateWith { id ->
                when (id) {
                    "ALL" -> roms.size
                    "FAV" -> roms.count { meta.roms[it.key]?.favorite == true }
                    else -> roms.count { it.console.id == id }
                }
            }
        }
        LandscapeLibrary(
            list = list, groups = groups, current = current,
            filters = chipIds, counts = counts, onStep = ::chip, onPick = ::pickFilter,
            mode = mode, onMode = { mode = it },
            searching = searching, query = query, onQuery = { query = it },
            onSearch = { searching = !searching; if (!searching) query = "" },
            sort = sort, onSort = { sort = if (sort == SortMode.Az) SortMode.Recent else SortMode.Az },
            focusedRom = focusedRom, known = known, pick = pick, heldGame = heldGame,
        )
        bar()
        return
    }

    val searchAndSort: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Select opens and closes the search: for the pad it is not a stop (issue 701).
            RoundIconButton(if (searching) Icons.Close else Icons.Search, stringResource(if (searching) R.string.library_close_search else R.string.library_search), {
                searching = !searching; if (!searching) query = ""
            }, padFocus = false)
            RoundIconButton(Icons.Sort, stringResource(R.string.library_sort_by, stringResource(sort.label)), {
                sort = if (sort == SortMode.Az) SortMode.Recent else SortMode.Az
            })
        }
    }
    // In tabletop the page's controls sit on the bottom half and the grid has the top one.
    if (tabletop) TabletopDeck {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { searchAndSort() }
                ViewToggle(mode) { mode = it }
            }
            if (searching) SearchField(query, autoFocus = searchRequested) { query = it }
            ConsoleChips(roms, current, Modifier.padding(top = 14.dp)) { current = it }
        }
    }

    // The list view as a table of time played and last played, read once per change off the main thread.
    val table = layout.tableList
    val revision by lib.revision.collectAsState()
    // Only while the table shows: the grid modes never read these files.
    val tableShown = table && mode == ViewMode.List
    val playtimes by produceState(emptyMap<String, Long>(), tableShown, roms, revision) {
        if (tableShown) value = withContext(Dispatchers.IO) { roms.associate { it.key to lib.playtimeSeconds(it) } }
    }
    // Zoomed, the caption follows its view's step (none at five columns; the art still names the game to TalkBack).
    val cartCaption = zoomGrid(LibraryZoomPref.carts, carts = true).captionSp
    val coverCaption = zoomGrid(LibraryZoomPref.covers, carts = false).captionSp
    // One game in the grid, in view mode m; a striped list row carries the zebra tint (issue 874).
    val tile: @Composable (Rom, ViewMode, Boolean) -> Unit = { rom, m, striped ->
            val romMeta = meta.roms[rom.key]
            val isPicked = pane && picked == rom.key
            val open = {
                if (!pane || isPicked) openFull(rom.key)
                else picked = rom.key
            }
            val focus = Modifier.holdsGame(rom.key, heldGame).restorableFocus(rom.key, focusedRom, initial = rom.key == firstKey).pickedRing(isPicked, outset = m != ViewMode.List).paneOpener(isPicked)
            val chosen = rom.key in selection
            var lit by remember { mutableStateOf(false) }
            when (m) {
                ViewMode.Carts -> CappedCell(GridSpec.Carts.takeUnless { zoom }) {
                    val caption = if (zoom) cartCaption else 13
                    Column(
                        it.gamepadFocus(corner = 10.dp, scale = 1.05f) { on -> lit = on }.then(focus).pickable(pick, rom.key, open),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.selectionLook(selection.active, chosen)) {
                            Cartridge(rom.console, labelFor(rom), rom.title, lib.effectiveColor(rom), Modifier.fillMaxWidth(), pending = labelPending(rom), cover = coverFor(rom), cropped = true)
                            TileBadges(selection.active, chosen, romMeta?.favorite == true)
                        }
                        if (caption != null) GameName(
                            rom.title, style = Type.Caption.copy(color = captionColor(selection.active, chosen), fontSize = caption.sp),
                            textAlign = TextAlign.Center, focused = lit,
                            modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
                        )
                    }
                }
                ViewMode.Covers -> CappedCell(GridSpec.Covers.takeUnless { zoom }) {
                    val caption = if (zoom) coverCaption else 12
                    Column(it.gamepadFocus(corner = 10.dp, scale = 1.05f) { on -> lit = on }.then(focus).pickable(pick, rom.key, open)) {
                        Box(Modifier.selectionLook(selection.active, chosen)) {
                            BoxArt(rom, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)), cropped = true)
                            TileBadges(selection.active, chosen, romMeta?.favorite == true)
                        }
                        if (caption != null) GameName(rom.title, style = Type.Caption.copy(color = captionColor(selection.active, chosen), fontSize = caption.sp), focused = lit, modifier = Modifier.padding(top = 6.dp).fillMaxWidth())
                    }
                }
                ViewMode.List -> if (table) TableRow(rom, playtimes[rom.key] ?: 0L, romMeta?.lastPlayed ?: 0L, romMeta?.favorite == true, focus, pick, open, striped && !isPicked)
                else Row(
                    Modifier.fillMaxWidth().gamepadFocus(corner = 12.dp, inset = true) { lit = it }.then(focus).pickable(pick, rom.key, open)
                        .listRowBackground(chosen, striped && !isPicked, selection.active)
                        .padding(vertical = LIST_ROW_PADDING_DP.dp, horizontal = if (selection.active) 8.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selection.active) CheckCircle(chosen, outline = Oe.Chevron, modifier = Modifier.padding(end = 12.dp))
                    Box(Modifier.size(listSize.artDp.dp), contentAlignment = Alignment.Center) {
                        BoxArt(rom, Modifier.clip(RoundedCornerShape(6.dp)), maxHeight = listSize.artDp.dp)
                    }
                    Column(Modifier.weight(1f).padding(start = listSize.textGapDp.dp)) {
                        val titleStyle = if (listSize == ListSize.ExtraLarge) Type.BodyMedium.copy(fontSize = listSize.titleSp.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp) else Type.BodyMedium
                        // One line scrolls when it does not fit (issue 869); the large rows wrap to two lines instead.
                        if (listSize.titleLines == 1) GameName(rom.title, style = titleStyle, focused = lit)
                        else Text(rom.title, style = titleStyle, maxLines = listSize.titleLines, overflow = TextOverflow.Ellipsis)
                        Text(rom.console.displayName, style = Type.Small.copy(fontSize = listSize.subtitleSp.sp))
                    }
                    if (romMeta?.favorite == true) Icon(Icons.Star, Oe.Star, 16.dp, filled = true)
                }
            }
    }
    // On a mixed page each console's group decides for itself, so each lays out its own columns and keeps its view's step.
    val perGroup = console == null && mode == ViewMode.Carts && groups.any { it.first?.frame == null }
    // As many columns as the width holds, never fewer than a phone shows.
    val spec = when (shown) { ViewMode.Carts -> GridSpec.Carts; ViewMode.Covers -> GridSpec.Covers; ViewMode.List -> GridSpec.List }
    // Zoomed, the shown view's step is the column count, whatever the width (the list keeps its one column).
    // Each view keeps its own step, and a grid follows the view it shows: covers take the cover view's
    // step even on a cartridge page, so zooming them never changes the cartridges (issue 868).
    val fixedColumns = zoomStep(shown).takeIf { zoom && shown != ViewMode.List }
    val zoomed = fixedColumns?.let { zoomGrid(it, carts = shown == ViewMode.Carts) }
    // Staggered, though every cell of a console's group is the same height (each cover is cropped to its
    // console's shape, issue 882), so with lanes of one width it fills row by row like a regular grid.
    // Held out here: crossing into or out of the pane moves the grid to another place in the tree, and it keeps its scroll.
    val gridState = rememberLazyStaggeredGridState()
    // A pinch's step, keeping the game under the fingers where it was: its index and its top, read before the grid reflows.
    fun pinch(bigger: Boolean, x: Float, y: Float) {
        val info = gridState.layoutInfo
        val anchor = anchorItem(info.visibleItemsInfo.map { ZoomSlot(it.index, it.offset.x, it.offset.y + info.beforeContentPadding, it.size.width, it.size.height) }, x, y)
        // The view whose step moves is the one under the fingers: on a mixed page, the console group's own,
        // whether the fingers are on its games or on its header.
        val key = anchor?.let { a -> info.visibleItemsInfo.firstOrNull { it.index == a.index }?.key } as? String
        val groupId = key?.let { k -> listOf("grid-", "group-").firstOrNull { k.startsWith(it) }?.let { k.removePrefix(it) } }
        val view = groupId?.takeIf { perGroup }?.let(Console::byId)?.let(::groupView) ?: shown
        val value = if (view == ViewMode.List) LibraryZoomPref.list.step(bigger).ordinal else stepColumns(zoomStep(view), bigger)
        if (!setZoom(view, value)) return
        zoomMoving = true
        zoomAnchor = ZoomAnchor(anchor?.index, anchor?.let { it.top - info.beforeContentPadding } ?: 0)
    }
    // The scroll back to the anchor waits for the composition that took the step: run straight from the
    // gesture it would measure the old columns. A newer step cancels this one, glide flag included.
    LaunchedEffect(zoomAnchor) {
        val a = zoomAnchor ?: return@LaunchedEffect
        if (a.index != null) {
            gridState.scrollToItem(a.index)
            gridState.scrollBy(-a.offset.toFloat())
        }
        delay(ZOOM_MOVE_MS + 50L)
        zoomMoving = false
    }
    val grid: @Composable () -> Unit = { MarginFocusScroll(trailing = FocusMargin + TabBarHeight + hintBarInset) { LazyVerticalStaggeredGrid(
        AdaptiveCells(spec, fixedColumns),
        // While selecting, the D-pad stays on the games: not off the side onto the tabs or the rail,
        // nor down onto the tabletop deck.
        Modifier.fillMaxSize().statusBarsPadding().holdFocusAtEnd(FocusDirection.Down).holdGamepadFocus(selection.active)
            .then(if (zoom) Modifier.pinchSteps { bigger, x, y -> pinch(bigger, x, y) } else Modifier),
        state = gridState,
        // A tablet's wider margins line the grid up with the shelf.
        contentPadding = contentSideDp(LocalWindowLayout.current).dp.let { side ->
            androidx.compose.foundation.layout.PaddingValues(start = side, end = side, top = FocusOutset, bottom = TabBarHeight + hintBarInset + 24.dp)
        },
        horizontalArrangement = Arrangement.spacedBy((zoomed?.gapDp ?: spec.gap).dp),
        verticalItemSpacing = if (shown == ViewMode.List) 0.dp else (zoomed?.rowGapDp ?: 18f).dp,
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            // While selecting, the D-pad stays on the games: the bar covers the header's top row.
            Column(Modifier.gamepadFence(selection.active)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { BackLink(stringResource(R.string.library_shelf_title)) { a.nav.popBackStack(Routes.SHELF, false) } }
                    if (!tabletop) searchAndSort()
                }
                if (searching && !tabletop) SearchField(query, autoFocus = searchRequested) { query = it }
                Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        val eyebrow = when (current) {
                            "FAV" -> stringResource(R.string.library_favorites_eyebrow)
                            "ALL" -> stringResource(R.string.library_all_consoles_eyebrow)
                            else -> "${console!!.short} · ${console.core(lib.consoleSettings(console).core).name}"
                        }
                        Text(eyebrow, style = Type.Eyebrow)
                        FitText(
                            when (current) { "FAV" -> stringResource(R.string.library_favorites); "ALL" -> stringResource(R.string.library_all_games); else -> console!!.displayName },
                            style = Type.Display, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Text(countString(R.plurals.library_games_count, list.size, list.size), style = Type.Small.copy(fontSize = 14.sp), modifier = Modifier.padding(bottom = 4.dp))
                }
                ArtworkSyncPill(Modifier.padding(top = 16.dp))
                if (!tabletop) ConsoleChips(roms, current, Modifier.padding(top = 14.dp)) { current = it }
                Row(Modifier.padding(top = 22.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(if (sort == SortMode.Az) R.string.library_section_carts else R.string.library_section_recent), style = Type.Section.copy(fontSize = 17.sp), modifier = Modifier.weight(1f))
                    if (zoom) SizeButton(shown, zoomStep(shown), { setZoom(shown, it) }, Modifier.padding(end = 6.dp))
                    if (!tabletop) ViewToggle(mode) { mode = it }
                }
                if (list.isEmpty() && known) {
                    Text(
                        if (query.isNotBlank()) stringResource(R.string.library_no_results, query) else stringResource(R.string.library_empty_list),
                        style = Type.Small, modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
        }
        if (table && shown == ViewMode.List && list.isNotEmpty()) item(key = "table-header", span = StaggeredGridItemSpan.FullLine) { TableHeader() }
        groups.forEach { (group, groupRoms) ->
            if (group != null) item(key = "group-${group.id}", span = StaggeredGridItemSpan.FullLine) { GroupHeader(group, groupRoms.size) }
            if (perGroup && group != null) {
                val groupMode = groupView(group)
                item(key = "grid-${group.id}", span = StaggeredGridItemSpan.FullLine) {
                    val groupSpec = if (groupMode == ViewMode.Covers) GridSpec.Covers else GridSpec.Carts
                    val groupColumns = zoomStep(groupMode)
                    GroupGrid(groupRoms, groupSpec, if (zoom) zoomGrid(groupColumns, carts = groupMode == ViewMode.Carts) else null, groupColumns) { tile(it, groupMode, false) }
                }
            } else {
                // The zebra restarts under each group's header: its first row is plain.
                itemsIndexed(groupRoms, key = { _, rom -> rom.key }) { index, rom ->
                    // The tiles glide to their new places after a zoom step, and only then: filtering and sorting stay instant.
                    Box(Modifier.animateItem(fadeInSpec = null, placementSpec = if (zoomMoving) tween(ZOOM_MOVE_MS) else null, fadeOutSpec = null)) { tile(rom, shown, zebraStriped(index)) }
                }
            }
        }
    } } }

    // The selection bar sits over the games, beside the pane rather than over it.
    val games: @Composable () -> Unit = { Box(Modifier.fillMaxSize()) { grid(); bar(); ZoomPillView(pill) { pill = null } } }
    if (!pane) {
        games()
        return
    }
    val pickedRom = picked?.takeIf { key -> roms.any { it.key == key } }
    ListDetailScaffold(
        listWidth = null, detailWidth = LIBRARY_PANE_DP.dp,
        // A pick whose game is gone (removed on its own screen) shows the placeholder, and Back must not stop there.
        selected = pickedRom != null, onClear = { picked = null },
        list = games,
        detail = { focusInDetail ->
            if (pickedRom == null) PanePlaceholder(Icons.Cart, stringResource(R.string.library_pick_hint), Modifier.padding(bottom = TabBarHeight))
            else key(pickedRom) {
                RomDetail(
                    pickedRom, DetailStyle.Pane, active = focusInDetail,
                    onClose = { picked = null }, onOpenFull = { openFull(pickedRom) },
                    onRenamed = { picked = it },
                )
            }
        },
        detailBackground = Oe.Surface,
    )
}

/** The picked game in the library beside its pane: a tinted card with an accent ring, [outset] around the item or inside a row. */
private fun Modifier.pickedRing(picked: Boolean, outset: Boolean): Modifier =
    if (!picked) this else this.semantics { selected = true }.drawWithContent {
        val off = if (outset) 6.dp.toPx() else 0f
        val radius = CornerRadius(16.dp.toPx())
        val topLeft = Offset(-off, -off)
        val area = Size(size.width + off * 2, size.height + off * 2)
        drawRoundRect(Oe.AccentTint, topLeft, area, radius)
        drawContent()
        val ring = 2.dp.toPx()
        drawRoundRect(Oe.AccentBright, Offset(-off + ring / 2, -off + ring / 2), Size(area.width - ring, area.height - ring), radius, style = Stroke(ring))
    }

/** The table's column names over the list view on a tablet; the rows say them to a screen reader. */
@Composable
private fun TableHeader() {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp).clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
        val caps = Type.Tag.copy(color = Oe.Muted, fontSize = 12.sp, letterSpacing = 1.sp)
        Text(stringResource(R.string.library_col_game).uppercase(), style = caps, modifier = Modifier.weight(1f).padding(start = 4.dp))
        Text(stringResource(R.string.library_col_time).uppercase(), style = caps, modifier = Modifier.width(96.dp))
        Text(stringResource(R.string.library_col_last).uppercase(), style = caps, modifier = Modifier.width(80.dp))
        Spacer(Modifier.width(24.dp))
    }
}

/** A game as a table row: its art, title and file, time played, when it was last played and its star. */
@Composable
private fun TableRow(rom: Rom, playtime: Long, lastPlayed: Long, favorite: Boolean, focus: Modifier, pick: TilePick, open: () -> Unit, striped: Boolean) {
    val lib = LocalActions.current.app.library
    val selecting = pick.selection.active
    val chosen = rom.key in pick.selection
    val time = formatPlaytime(playtime)
    val last = if (lastPlayed > 0) formatAgo(lastPlayed) else ""
    // Never played: the time already says so, and "last played never played" says it twice.
    val description = if (last.isEmpty()) "${rom.title}, $time" else stringResource(R.string.library_row_description, rom.title, time, last)
    var lit by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).gamepadFocus(corner = 12.dp, inset = true) { lit = it }.then(focus)
            .semantics { contentDescription = description }.pickable(pick, rom.key, open)
            .listRowBackground(chosen, striped, selecting)
            .padding(vertical = 6.dp, horizontal = if (selecting) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) CheckCircle(chosen, outline = Oe.Chevron, modifier = Modifier.padding(end = 12.dp))
        Row(Modifier.weight(1f).clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(76.dp), contentAlignment = Alignment.Center) {
                if (rom.console.frame != null) Cartridge(rom.console, labelFor(rom), rom.title, lib.effectiveColor(rom), Modifier.width(76.dp), pending = labelPending(rom), cover = coverFor(rom))
                else BoxArt(rom, Modifier.clip(RoundedCornerShape(6.dp)), maxHeight = 56.dp)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp, end = 10.dp)) {
                GameName(rom.title, style = Type.BodyMedium.copy(fontSize = 15.sp), focused = lit)
                Text(rom.file.name, style = Type.Small.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(time, style = Type.Small, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(96.dp))
            Text(last, style = Type.Small, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(80.dp))
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { if (favorite) Icon(Icons.Star, Oe.Star, 16.dp, filled = true) }
        }
    }
}

/** Whether the list row at [indexInGroup] carries the zebra tint: every second row, the group's first one plain. */
internal fun zebraStriped(indexInGroup: Int): Boolean = indexInGroup % 2 == 1

/**
 * A list row's card: the accent when it is chosen, else the zebra tint on a striped row, else nothing.
 * Outside selection the row has no inner padding, so the stripe reaches [LIST_STRIPE_OUTSET_DP] past
 * each side into the grid's margin: the art and the star keep their place and never touch its edge.
 */
private fun Modifier.listRowBackground(chosen: Boolean, striped: Boolean, selecting: Boolean): Modifier = when {
    chosen -> background(Oe.AccentSoft, ListRowShape)
    striped -> drawBehind {
        val off = if (selecting) 0f else LIST_STRIPE_OUTSET_DP.dp.toPx()
        drawRoundRect(Oe.RowAlt, Offset(-off, 0f), Size(size.width + off * 2, size.height), CornerRadius(12.dp.toPx()))
    }
    else -> this
}

/** How far a stripe reaches past a list row's sides outside selection: the inset a selected row's content takes. */
private const val LIST_STRIPE_OUTSET_DP = 8

/** The rounded card behind a list row, shared by the selection and the zebra stripe. */
private val ListRowShape = RoundedCornerShape(12.dp)

/** What a tap and a long press on a game do: open it and start selecting, or toggle it while selecting. */
private class TilePick(
    val selection: RomSelection,
    val select: String,
    val deselect: String,
    val onStart: (String) -> Unit,
    val onToggle: (String) -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.pickable(pick: TilePick, key: String, open: () -> Unit): Modifier =
    if (pick.selection.active) {
        val picked = key in pick.selection
        semantics { selected = picked; toggleableState = ToggleableState(picked) }
            .combinedClickable(
                onClickLabel = if (picked) pick.deselect else pick.select, role = Role.Checkbox,
                onClick = { pick.onToggle(key) }, onLongClick = { pick.onToggle(key) },
            )
    } else {
        combinedClickable(onClick = open, onLongClick = { pick.onStart(key) })
    }

/** A selected game's ring, and the others dimmed while selecting; nothing outside selection mode. */
private fun Modifier.selectionLook(selecting: Boolean, picked: Boolean): Modifier = when {
    picked -> border(3.dp, Oe.AccentBright, RoundedCornerShape(10.dp))
    selecting -> alpha(0.78f)
    else -> this
}

@Composable
private fun captionColor(selecting: Boolean, picked: Boolean) = if (selecting && !picked) Oe.Muted else Oe.Text

/** The marker at the top start of a tile while selecting; the favorite star moves to the other corner. */
@Composable
private fun BoxScope.TileBadges(selecting: Boolean, picked: Boolean, favorite: Boolean) {
    if (selecting) CheckCircle(picked, outline = Color.White.copy(alpha = 0.85f), hollow = Color.Black.copy(alpha = 0.35f), modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
    if (favorite) FavBadge(Modifier.align(if (selecting) Alignment.TopEnd else Alignment.TopStart).padding(4.dp))
}

/** Selected: an accent disc with a check, haloed; not selected: a hollow ring. Never colour alone. */
@Composable
private fun CheckCircle(picked: Boolean, outline: Color, modifier: Modifier = Modifier, hollow: Color = Color.Transparent) {
    Box(modifier.size(24.dp), contentAlignment = Alignment.Center) {
        if (picked) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(Oe.Accent).border(2.dp, Oe.Bg, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Check, Color.White, 16.dp, stroke = 2.5f) }
        } else {
            Box(Modifier.size(20.dp).clip(CircleShape).background(hollow).border(2.dp, outline, CircleShape))
        }
    }
}

/** The contextual bar over the top of the library while selecting. */
@Composable
private fun SelectionBar(count: Int, allSelected: Boolean, onClose: () -> Unit, onSelectAll: () -> Unit, onShare: () -> Unit, onRemove: () -> Unit) {
    val short = LocalWindowLayout.current.short
    Column(
        Modifier.fillMaxWidth().background(Oe.Surface)
            // Taps on the bar never reach the header it covers.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            // The gamepad has X, Y and B for these, and the D-pad stays on the games: A means "Select".
            .gamepadFence(closed = true)
            .statusBarsPadding().displayCutoutPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().height(if (short) 52.dp else 56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoundIconButton(Icons.Close, stringResource(R.string.library_selection_close), onClose, background = Color.Transparent)
            Text(
                countString(R.plurals.library_selected_count, count, count), style = Type.Section.copy(fontSize = 17.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.height(40.dp).alpha(if (allSelected) 0.4f else 1f)
                    .clip(RoundedCornerShape(20.dp)).background(Oe.Control)
                    .clickable(enabled = !allSelected, role = Role.Button, onClick = onSelectAll)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.library_select_all), style = Type.Section.copy(fontSize = 14.sp, color = Oe.AccentText), maxLines = 1) }
            RoundIconButton(
                Icons.Share, countString(R.plurals.library_share_selection, count, count), onShare,
                tint = Oe.AccentOnSoft, background = Oe.AccentSoft,
            )
            RoundIconButton(Icons.Trash, stringResource(R.string.library_remove_selected), onRemove, tint = Oe.Danger, background = Oe.DangerBg)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Oe.Border))
    }
}

/** A group's games in as many staggered columns as [spec] fits, filled in turn like the grid's lanes. */
@Composable
private fun GroupGrid(roms: List<Rom>, spec: GridSpec, zoomed: ZoomGrid?, zoomColumns: Int, tile: @Composable (Rom) -> Unit) = BoxWithConstraints(Modifier.fillMaxWidth()) {
    val columns = if (zoomed != null) zoomColumns else gridColumns(maxWidth.value, spec.min, spec.gap, spec.floor)
    val gap = (zoomed?.gapDp ?: spec.gap).dp
    // Columns of one width in whole pixels (the grid's own lanes), so equal-shaped tiles are equally tall
    // in each and the rows stay level: widths in dp round per column and can overrun the row by a pixel.
    val cell = with(LocalDensity.current) { evenCellSizes(constraints.maxWidth, gap.roundToPx(), columns)[0].toDp() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
        repeat(columns) { c ->
            Column(Modifier.width(cell), verticalArrangement = Arrangement.spacedBy((zoomed?.rowGapDp ?: 18f).dp)) {
                roms.filterIndexed { i, _ -> i % columns == c }.forEach { tile(it) }
            }
        }
    }
}

/** The grid's lanes: [fixed] of them when zoomed, else as many as [spec] fits in the width, all of one width. */
private data class AdaptiveCells(private val spec: GridSpec, private val fixed: Int? = null) : StaggeredGridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): IntArray =
        evenCellSizes(availableSize, spacing, fixed ?: gridColumns(availableSize.toDp().value, spec.min, spacing.toDp().value, spec.floor))
}

/** A card no wider than [spec]'s maximum (as wide as its cell with none), centred in a wider cell; [content] gets the capped modifier. */
@Composable
private fun CappedCell(spec: GridSpec?, content: @Composable (Modifier) -> Unit) =
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        content(if (spec == null) Modifier.fillMaxWidth() else Modifier.widthIn(max = spec.max.dp).fillMaxWidth())
    }

/** The view a console's group shows on a mixed cartridge page: its covers when it has no cartridge art. */
private fun groupView(console: Console?): ViewMode = if (console?.frame == null) ViewMode.Covers else ViewMode.Carts

/** The item a zoom step keeps in place ([index] null for none) and its offset from the grid's content top; a new one per step. */
private class ZoomAnchor(val index: Int?, val offset: Int)

/** How long the tiles take to glide to their places after a zoom step. */
private const val ZOOM_MOVE_MS = 220

/** The step a zoom just took, named for a moment over the grid; a new one each time, so a repeat restarts the timer. */
private class ZoomPill(val mode: ViewMode, val value: Int)

/** A step's name: "3 columns" in the grid views, the row size in the list. */
@Composable
private fun zoomStepName(mode: ViewMode, value: Int): String = when (mode) {
    ViewMode.List -> stringResource(listSizeName(ListSize.entries[value]))
    else -> countString(R.plurals.library_size_columns, value, value)
}

@StringRes
private fun listSizeName(size: ListSize): Int = when (size) {
    ListSize.Compact -> R.string.library_size_compact
    ListSize.Normal -> R.string.library_size_normal
    ListSize.Large -> R.string.library_size_large
    ListSize.ExtraLarge -> R.string.library_size_extra_large
}

/** The pill naming a pinch's step for about a second, in the middle of the grid. */
@Composable
private fun BoxScope.ZoomPillView(pill: ZoomPill?, onDone: () -> Unit) {
    // The last step stays named while the pill fades out.
    var shown by remember { mutableStateOf(pill) }
    if (pill != null) shown = pill
    LaunchedEffect(pill) { if (pill != null) { delay(1000); onDone() } }
    AnimatedVisibility(pill != null, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
        shown?.let { p ->
            Text(
                zoomStepName(p.mode, p.value), style = Type.BodyMedium.copy(color = Oe.Bg, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.background(Oe.Text, RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 10.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/**
 * Two fingers spreading or closing take the grid one step per pinch (issue 868): [onStep] gets whether the
 * items grow and where between the fingers it happened. One finger is never touched, so scrolling, taps
 * and long presses work as before; once a second finger is down the gesture is the pinch's to its end,
 * so the grid does not scroll under it and the game under a finger does not open or start a selection.
 */
private fun Modifier.pinchSteps(onStep: (bigger: Boolean, x: Float, y: Float) -> Unit): Modifier = composed {
    val step by rememberUpdatedState(onStep)
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var pinching = false
            var pair: Pair<Long, Long>? = null
            var start = 0f
            var stepped = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val down = event.changes.filter { it.pressed }
                if (down.isEmpty()) break
                if (down.size >= 2) {
                    pinching = true
                    val (a, b) = down
                    val spread = (a.position - b.position).getDistance()
                    // A different pair of fingers starts its own measure.
                    if (pair != a.id.value to b.id.value) { pair = a.id.value to b.id.value; start = spread }
                    if (!stepped && start > 0f) pinchStep(spread / start)?.let { bigger ->
                        stepped = true
                        val mid = (a.position + b.position) / 2f
                        step(bigger, mid.x, mid.y)
                    }
                }
                if (pinching) event.changes.forEach { it.consume() }
            }
        }
    }
}

/** The view's Size button and its menu: the pinch's steps for whoever cannot pinch, and for the pad. */
@Composable
private fun SizeButton(mode: ViewMode, current: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.library_size)
    Box(modifier) {
        Box(
            Modifier.size(padSize(SizeButtonSize, SizeButtonSizePad)).gamepadFocus(corner = 10.dp).clip(RoundedCornerShape(10.dp))
                .background(if (open) Oe.Raised else Oe.Surface)
                .semantics { contentDescription = label }
                .clickable(role = Role.Button) { open = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Size, if (open) Oe.Text else Oe.Muted, 18.dp) }
        val opener = LocalPadScope.current
        // Not keyed on [open]: a new scope while the menu animates out would land focus in the closing popup.
        val menuScope = remember { PadScope() }
        val steps = if (mode == ViewMode.List) ListSize.entries.map { it.ordinal } else (ZOOM_MIN_COLUMNS..ZOOM_MAX_COLUMNS).toList()
        CompositionLocalProvider(LocalPadScope provides menuScope) { androidx.compose.material3.DropdownMenu(
            open, { open = false }, Modifier.padPopup(menuScope, opener) { open = false }, containerColor = Oe.Surface2,
        ) {
            Text(label.uppercase(), style = Type.SectionCaps, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp).semantics { heading() })
            steps.forEach { value ->
                val on = value == current
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(zoomStepName(mode, value), style = Type.Body.copy(color = if (on) Oe.AccentOnSoft else Oe.Text)) },
                    trailingIcon = { if (on) Icon(Icons.Check, Oe.AccentOnSoft, 18.dp) },
                    modifier = Modifier.gamepadFocus(corner = 0.dp, inset = true).background(if (on) Oe.AccentSoft else Color.Transparent)
                        .semantics { selected = on },
                    onClick = { open = false; onPick(value) },
                )
            }
        } }
    }
}

/** The Size button: as tall as the view toggle beside it, a little larger in the gamepad layout. */
private val SizeButtonSize = DpSize(38.dp, 38.dp)
private val SizeButtonSizePad = DpSize(46.dp, 46.dp)

/** A console's header over its games on a mixed page. */
@Composable
private fun GroupHeader(console: Console, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ConsoleIcon(console, 24.dp)
        Text(groupCaption(console), style = Type.Section.copy(fontSize = 17.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(countString(R.plurals.library_games_count, count, count), style = Type.Caption)
    }
}

/** A group's caption: the console's abbreviation and its name, "SNES – Super Nintendo". */
@Composable
private fun groupCaption(console: Console) = stringResource(R.string.library_group_caption, console.short, console.displayName)

/** In the sideways row, the small label over a console's games: icon, caption and count on one line. */
@Composable
private fun GroupLabelLine(console: Console, count: Int, modifier: Modifier) {
    Row(
        modifier.height(18.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConsoleIcon(console, 16.dp)
        Text(groupCaption(console), style = Type.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp).weight(1f, fill = false))
        Text("·", style = Type.Caption.copy(color = Oe.Faint), modifier = Modifier.padding(horizontal = 6.dp).clearAndSetSemantics {})
        Text(countString(R.plurals.library_games_count, count, count), style = Type.Caption.copy(color = Oe.Faint), maxLines = 1, softWrap = false)
    }
}

/**
 * Over the landscape row of a mixed page, one label per console in view: over
 * the console's first game, stuck at the row's start ([edge]) once that game
 * scrolls past, pushed out by the next console's label. Positions are read at
 * layout time, so scrolling only moves them.
 *
 * Accessibility drops a node that a later sibling covers, and the row covers
 * the labels drawn under it. So the labels come twice: [semanticsOnly] false
 * draws them under the row with no semantics, true lays them out over the row
 * drawing nothing, for TalkBack to read.
 */
@Composable
private fun GroupLabels(groups: List<Pair<Console?, List<Rom>>>, state: LazyListState, edge: Dp, semanticsOnly: Boolean, modifier: Modifier) {
    val starts = remember(groups) { GroupLabel.starts(groups.map { it.second.size }) }
    // The groups with a game in view; changes only when a group enters or leaves.
    val inView by remember(groups, starts) {
        derivedStateOf {
            val visible = state.layoutInfo.visibleItemsInfo
            if (visible.isEmpty() || starts.isEmpty()) emptyList()
            else (GroupLabel.groupAt(visible.first().index, starts)..GroupLabel.groupAt(visible.last().index, starts)).toList()
        }
    }
    val widths = remember { mutableStateMapOf<String, Int>() }
    val density = LocalDensity.current
    val edgePx = with(density) { edge.roundToPx() }
    val gapPx = with(density) { 12.dp.roundToPx() }
    // Where group [g]'s label sits, from the last group in view back to [g].
    fun labelX(g: Int): Int {
        val info = state.layoutInfo
        val startX = info.visibleItemsInfo.firstOrNull { it.index == starts[g] }?.let { it.offset - info.viewportStartOffset }
        val next = g + 1
        val nextX = if (next in inView && groups[next].first != null) labelX(next) else null
        return GroupLabel.stickyX(startX, nextX, groups[g].first?.let { widths[it.id] } ?: 0, edgePx, gapPx)
    }
    BoxWithConstraints(modifier.fillMaxWidth().height(18.dp).clipToBounds()) {
        inView.forEach { g ->
            val console = groups[g].first ?: return@forEach
            key(console.id) {
                GroupLabelLine(
                    console, groups[g].second.size,
                    (if (semanticsOnly) Modifier.drawWithContent {} else Modifier.clearAndSetSemantics {})
                        .widthIn(max = maxWidth - edge * 2)
                        .onSizeChanged { widths[console.id] = it.width }
                        .offset { IntOffset(labelX(g), 0) },
                )
            }
        }
    }
}

/** A game's box art: [cropped] to its console's cover shape in the cover views, whole in the list. */
@Composable
private fun BoxArt(rom: Rom, modifier: Modifier, maxHeight: Dp? = null, cropped: Boolean = false) =
    CoverArt(coverFor(rom), rom.title, modifier, maxHeight, aspect = if (cropped) rom.console.coverAspect else null)

/**
 * Landscape: one header row (the filter pager in it, issue 669) and one row of games that scrolls
 * sideways, three items at most in view.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LandscapeLibrary(
    list: List<Rom>,
    groups: List<Pair<Console?, List<Rom>>>,
    current: String,
    filters: List<String>,
    counts: Map<String, Int>,
    onStep: (Int) -> Unit,
    onPick: (String) -> Unit,
    mode: ViewMode,
    onMode: (ViewMode) -> Unit,
    searching: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    sort: SortMode,
    onSort: () -> Unit,
    focusedRom: MutableState<String?>,
    known: Boolean,
    pick: TilePick,
    heldGame: MutableState<String?>,
) {
    val a = LocalActions.current
    val lib = a.app.library
    val meta by lib.meta.collectAsState()
    Column(Modifier.fillMaxSize().statusBarsPadding().displayCutoutPadding().padding(bottom = TabBarHeight + hintBarInset)) {
        // The header sits under the top edge: the room above it is the focus ring's. One row only: the
        // search field takes the pager's place in it, and the filters are paged instead of a row of chips.
        BoxWithConstraints(Modifier.padding(start = 20.dp, end = 20.dp, top = FocusOutset).fillMaxWidth()) {
            val band = headerBand(maxWidth.value, InputMode.padLayout)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundIconButton(Icons.Back, stringResource(R.string.library_shelf_title), { a.nav.popBackStack(Routes.SHELF, false) }, padFocus = false)
                Box(Modifier.weight(1f)) {
                    if (searching) {
                        val placeholder = if (current == "ALL") stringResource(R.string.library_search_all)
                        else stringResource(R.string.library_search_in, filterName(current))
                        SearchField(query, autoFocus = false, placeholder = placeholder, topPadding = 0.dp, onChange = onQuery)
                    } else {
                        FilterPager(band, filters, current, list.size, counts, onStep, onPick)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundIconButton(if (searching) Icons.Close else Icons.Search, stringResource(if (searching) R.string.library_close_search else R.string.library_search), onSearch, padFocus = false)
                    RoundIconButton(Icons.Sort, stringResource(R.string.library_sort_by, stringResource(sort.label)), onSort)
                    Box(Modifier.padding(start = 4.dp)) { ViewToggle(mode, onMode) }
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val margin = 32.dp
            val gap = 24.dp
            val third = (maxWidth - margin * 2 - gap * 2) / 3
            val caption = 28.dp
            // Room above and below, so nothing touches the edges.
            val itemH = maxHeight - caption - 56.dp
            if (list.isEmpty() && known) {
                Text(
                    if (query.isNotBlank()) stringResource(R.string.library_no_results, query) else stringResource(R.string.library_empty_list),
                    style = Type.Small, modifier = Modifier.align(Alignment.Center),
                )
            }
            // The focused game stays in the middle; the first and last rest at the row's edges.
            // The last game sits at the row's end, which is the left in a right-to-left layout.
            val rowEnd = if (LocalLayoutDirection.current == LayoutDirection.Rtl) FocusDirection.Left else FocusDirection.Right
            // A mixed page names each console over its games, under the row so a focus ring is never covered.
            val rowState = rememberLazyListState()
            val mixed = groups.any { it.first != null }
            if (mixed) GroupLabels(groups, rowState, margin, semanticsOnly = false, Modifier.padding(top = 8.dp))
            CenteredFocusScroll { LazyRow(
                // While selecting, the D-pad stays on the games, as in the portrait grid.
                Modifier.fillMaxSize().holdFocusAtEnd(rowEnd).holdGamepadFocus(pick.selection.active),
                state = rowState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = margin),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val firstKey = groups.firstOrNull()?.second?.firstOrNull()?.key
                groups.forEach { (_, groupRoms) ->
                    items(groupRoms, key = { it.key }) { rom ->
                        val open = { a.nav.navigate(Routes.rom(rom.key)) }
                        val favorite = meta.roms[rom.key]?.favorite == true
                        // At most three items a row: the cartridge fills the height it has, and a wide one
                        // widens its item past a third. A console without cartridge art shows its cover.
                        val itemMode = if (mode == ViewMode.Carts && rom.console.frame == null) ViewMode.Covers else mode
                        val w = if (itemMode == ViewMode.Carts) minOf((maxWidth - margin * 2) * 0.42f, itemH * rom.console.cartAspect) else null
                        var lit by remember { mutableStateOf(false) }
                        Column(
                            Modifier.width(maxOf(third, w ?: third)).gamepadFocus(corner = 10.dp, scale = 1.05f) { lit = it }
                                .holdsGame(rom.key, heldGame)
                                .restorableFocus(rom.key, focusedRom, initial = rom.key == firstKey)
                                .pickable(pick, rom.key, open),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val selecting = pick.selection.active
                            val picked = rom.key in pick.selection
                            Box(Modifier.selectionLook(selecting, picked)) {
                                if (itemMode == ViewMode.Carts) {
                                    Cartridge(rom.console, labelFor(rom), rom.title, lib.effectiveColor(rom), Modifier.width(w!!), pending = labelPending(rom), cover = coverFor(rom))
                                } else {
                                    BoxArt(rom, Modifier.clip(RoundedCornerShape(10.dp)), maxHeight = itemH, cropped = true)
                                }
                                TileBadges(selecting, picked, favorite)
                            }
                            // With the gamepad, the focused game's title stands out from the others.
                            GameName(
                                rom.title,
                                style = Type.Caption.copy(
                                    color = if ((InputMode.gamepad && !lit) || (selecting && !picked)) Oe.Muted else Oe.Text, fontSize = 13.sp,
                                    fontWeight = if (lit) FontWeight.SemiBold else Type.Caption.fontWeight,
                                ),
                                textAlign = TextAlign.Center, focused = lit,
                                modifier = Modifier.padding(top = 6.dp).fillMaxWidth(0.82f),
                            )
                        }
                    }
                }
            } }
            // The same labels over the row, invisible, so the row does not hide them from TalkBack.
            if (mixed) GroupLabels(groups, rowState, margin, semanticsOnly = true, Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ConsoleChips(roms: List<Rom>, current: String, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    val library = LocalActions.current.app.library
    val meta by library.meta.collectAsState()
    FocusRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val chips = listOf("ALL" to stringResource(R.string.library_chip_all), "FAV" to stringResource(R.string.library_favorites)) +
            library.orderedConsoles(meta.consoleOrder).filter { c -> roms.any { it.console == c } }.map { it.id to it.short }
        // In the gamepad layout the shoulders that switch the chip sit at the row's ends.
        val shoulders = InputMode.padLayout && chips.size > 1
        if (shoulders) ShoulderPill("L1")
        chips.forEach { (id, label) ->
            val active = id == current
            Box(
                // L1/R1 step through these: for the pad they are not a stop (issue 701).
                Modifier.height(padSize(ChipHeight, ChipHeightPad)).touchOnly().clip(RoundedCornerShape(18.dp))
                    .background(if (active) Oe.Text else Oe.Surface)
                    .let { if (active) it else it.border(1.dp, Oe.Border, RoundedCornerShape(18.dp)) }
                    .clickable(role = Role.Tab) { onPick(id) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Type.Section.copy(fontSize = 14.sp, color = if (active) Oe.Bg else Oe.Text))
            }
        }
        if (shoulders) ShoulderPill("R1")
    }
}

/** A filter's full name: the console's, or the "All games" / "Favorites" page's. */
@Composable
private fun filterName(id: String): String = when (id) {
    "ALL" -> stringResource(R.string.library_all_games)
    "FAV" -> stringResource(R.string.library_favorites)
    else -> Console.byId(id)?.displayName ?: id
}

/** A filter's short name, as its chip read it: for the narrowest header. */
@Composable
private fun filterShortName(id: String): String = when (id) {
    "ALL" -> stringResource(R.string.library_chip_all)
    "FAV" -> stringResource(R.string.library_favorites)
    else -> Console.byId(id)?.short ?: id
}

/** A filter's icon: the console's, a star for the favorites, none for all games. */
@Composable
private fun FilterIcon(id: String) {
    when (id) {
        "ALL" -> Unit
        "FAV" -> Icon(Icons.Star, Oe.Star, 20.dp, filled = true)
        else -> Console.byId(id)?.let { ConsoleIcon(it, 20.dp) }
    }
}

/**
 * The landscape header's console filter (issue 669): previous, the current filter (which opens a menu
 * of them all) and next, walking the same filters in the same order the chips did.
 */
@Composable
private fun FilterPager(
    band: HeaderBand,
    filters: List<String>,
    current: String,
    shown: Int,
    counts: Map<String, Int>,
    onStep: (Int) -> Unit,
    onPick: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (band.steps) FilterStep(-1, filters, current, onStep)
        Box(Modifier.weight(1f, fill = false)) { FilterLabel(band, filters, current, shown, counts, onPick) }
        if (band.steps) FilterStep(1, filters, current, onStep)
    }
}

/**
 * One step of the pager: a chevron for touch, the shoulder that does the same in the gamepad layout.
 * At an end it dims and does nothing. L1/R1 are the pad's way to it, so it is not a focus stop (issue 701).
 */
@Composable
private fun FilterStep(step: Int, filters: List<String>, current: String, onStep: (Int) -> Unit) {
    val enabled = canStep(filters, current, step)
    val label = stringResource(
        if (step < 0) R.string.library_previous_filter else R.string.library_next_filter,
        filterName(stepFilter(filters, current, step)),
    )
    Box(
        Modifier.size(padSize(StepSize, StepSizePad)).touchOnly().clip(CircleShape)
            .clickable(onClickLabel = label, role = Role.Button) { if (enabled) onStep(step) }
            .semantics { contentDescription = label; if (!enabled) disabled() }
            .alpha(if (enabled) 1f else 0.38f),
        contentAlignment = Alignment.Center,
    ) {
        if (InputMode.padLayout) ShoulderPill(if (step < 0) "L1" else "R1")
        else Icon(if (step < 0) Icons.Back else Icons.Chevron, Oe.Text, 20.dp)
    }
}

/** The current filter: its icon, name and count, opening the menu of every filter. */
@Composable
private fun FilterLabel(band: HeaderBand, filters: List<String>, current: String, shown: Int, counts: Map<String, Int>, onPick: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val numbers = rememberCountFormat()
    val name = filterName(current)
    val description = stringResource(R.string.library_filter_label, name, countString(R.plurals.library_games_count, shown, shown))
    Box {
        Row(
            Modifier.height(padSize(StepSize, StepSizePad)).gamepadFocus(corner = 12.dp).clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) { menu = true }
                .semantics { contentDescription = description }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (band.icon) FilterIcon(current)
            FilterName(
                name, filterShortName(current), band.shortName,
                style = Type.Title.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Oe.Text),
                modifier = Modifier.weight(1f, fill = false).clearAndSetSemantics {},
            )
            if (band.count) Text(
                numbers.format(shown), style = Type.Small.copy(fontSize = 13.sp, color = Oe.Muted),
                modifier = Modifier.clearAndSetSemantics {},
            )
            if (band.caret) Icon(if (menu) Icons.ChevronUp else Icons.ChevronDown, Oe.Muted, 14.dp)
        }
        FilterMenu(menu, { menu = false }, filters, current, counts) { menu = false; onPick(it) }
    }
}

/**
 * The current filter's name, shrunk to fit the room it has, never cut: when the full [name] is still
 * wider than that at the smallest size [FitText] goes to (a long console name on a 640dp phone), the
 * [short] one, as the chip read it, takes its place. [forceShort] is the narrowest band's.
 */
@Composable
private fun FilterName(name: String, short: String, forceShort: Boolean, style: TextStyle, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val smallest = style.copy(fontSize = style.fontSize * FIT_TEXT_MIN_SCALE)
        val fullWidth = remember(measurer, name, smallest) { measurer.measure(name, smallest, maxLines = 1, softWrap = false).size.width }
        val text = nameThatFits(name, short, forceShort, fullWidth, constraints.maxWidth)
        FitText(text, style)
    }
}

/**
 * Digits for a count in the app's language: the configuration's locale, not the JVM default, which
 * below API 33 stays the system's when a language is picked in the settings (see AppLocale).
 */
@Composable
private fun rememberCountFormat(): NumberFormat {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { NumberFormat.getIntegerInstance(locale) }
}

/** Every filter, for a direct pick: a popup the pad drives on its own, as the detail screen's menu. */
@Composable
private fun FilterMenu(open: Boolean, onDismiss: () -> Unit, filters: List<String>, current: String, counts: Map<String, Int>, onPick: (String) -> Unit) {
    val opener = LocalPadScope.current
    // Not keyed on [open]: a new scope while the menu animates out would land focus in the closing popup.
    val menuScope = remember { PadScope() }
    val numbers = rememberCountFormat()
    CompositionLocalProvider(LocalPadScope provides menuScope) { androidx.compose.material3.DropdownMenu(
        open, onDismiss, Modifier.padPopup(menuScope, opener, onDismiss), containerColor = Oe.Surface2,
    ) {
        filters.forEach { id ->
            val on = id == current
            val color = if (on) Oe.AccentOnSoft else Oe.Text
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(filterName(id), style = Type.Body.copy(color = color)) },
                // "All" has no icon; its room is kept so the names line up.
                leadingIcon = { Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { FilterIcon(id) } },
                trailingIcon = { Text(numbers.format(counts[id] ?: 0), style = Type.Small.copy(fontSize = 13.sp, color = Oe.Muted)) },
                modifier = Modifier.gamepadFocus(corner = 0.dp, inset = true).background(if (on) Oe.AccentSoft else Color.Transparent)
                    .semantics { selected = on },
                onClick = { onPick(id) },
            )
        }
    } }
}

/** The pager's steps and label: as tall as the chips they replace, a little taller in the gamepad layout. */
private val StepSize = 40.dp
private val StepSizePad = 44.dp

/**
 * A shoulder button's name beside the chips it switches, as the hint bar draws its badges (LB, L or L1
 * with the pad in use); not focusable.
 */
@Composable
private fun ShoulderPill(button: String) {
    val face = (padFaces(button).singleOrNull() as? Face.Text)?.text ?: button
    Box(Modifier.background(Oe.Raised, RoundedCornerShape(11.dp)).padding(horizontal = 8.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
        Text(face, style = Type.Tag.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Oe.Text))
    }
}

@Composable
private fun SearchField(
    value: String,
    autoFocus: Boolean,
    placeholder: String = stringResource(R.string.library_search_hint),
    topPadding: Dp = 10.dp,
    onChange: (String) -> Unit,
) {
    val focus = remember { FocusRequester() }
    Row(
        Modifier.padding(top = topPadding).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(Oe.Surface)
            .border(1.dp, Oe.Border, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Search, Oe.Muted, 18.dp)
        Box(Modifier.weight(1f).padding(start = 10.dp)) {
            if (value.isEmpty()) Text(placeholder, style = Type.Body.copy(color = Oe.Faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = Type.Body, cursorBrush = SolidColor(Oe.AccentText),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
    }
    LaunchedEffect(Unit) { if (autoFocus) runCatching { focus.requestFocus() } }
}

@Composable
private fun FavBadge(modifier: Modifier = Modifier) {
    Box(modifier.size(24.dp).clip(CircleShape).background(Oe.Bg.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Star, Oe.Star, 13.dp, filled = true)
    }
}

@Composable
private fun ViewToggle(mode: ViewMode, onChange: (ViewMode) -> Unit) {
    // A shaped background, not a clip: a clip would cut the focused button's ring.
    Row(Modifier.background(Oe.Surface, RoundedCornerShape(12.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(ViewMode.Carts to Icons.Cart, ViewMode.Covers to Icons.Grid, ViewMode.List to Icons.List).forEach { (m, icon) ->
            val on = m == mode
            Box(
                Modifier.size(padSize(ToggleSize, ToggleSizePad)).gamepadFocus(corner = 9.dp).clip(RoundedCornerShape(9.dp)).background(if (on) Oe.Raised else Color.Transparent)
                    .clickable(role = Role.Tab) { onChange(m) },
                contentAlignment = Alignment.Center,
            ) { Icon(icon, if (on) Oe.Text else Oe.Muted, 18.dp) }
        }
    }
}

@Composable
fun RecentScreen() {
    val a = LocalActions.current
    val lib = a.app.library
    val roms by lib.roms.collectAsState()
    val meta by lib.meta.collectAsState()
    val loaded by lib.loaded.collectAsState()
    val recent = roms.filter { (meta.roms[it.key]?.lastPlayed ?: 0) > 0 }.sortedByDescending { meta.roms[it.key]!!.lastPlayed }
    GamepadButtons(hints = listOf("A" to stringResource(R.string.library_hint_detail), "L1 R1" to stringResource(R.string.library_hint_tabs)))

    MarginFocusScroll(trailing = FocusMargin + TabBarHeight + hintBarInset) { LazyColumn(Modifier.fillMaxSize().statusBarsPadding().holdFocusAtEnd(FocusDirection.Down)) {
        item {
            Text(stringResource(R.string.library_recent_title), style = Type.Display, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp))
        }
        if (recent.isEmpty() && shelfContent(loaded, roms) != ShelfContent.Loading) {
            item { EmptyState(stringResource(R.string.library_recent_empty_title), stringResource(R.string.library_recent_empty_body), null, {}) }
        }
        items(recent, key = { it.key }) { rom ->
            var lit by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().gamepadFocus(corner = 0.dp, inset = true) { lit = it }.clickable { a.nav.navigate(Routes.rom(rom.key)) }.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cartridge(rom.console, labelFor(rom), rom.title, lib.effectiveColor(rom), Modifier.width(96.dp), cover = coverFor(rom))
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    GameName(rom.title, style = Type.BodyMedium, focused = lit)
                    Text("${rom.console.short} · ${formatAgo(meta.roms[rom.key]!!.lastPlayed)}", style = Type.Small, modifier = Modifier.padding(top = 2.dp))
                }
                Box(
                    Modifier.size(44.dp).gamepadFocus(corner = 22.dp).clip(CircleShape).background(Oe.Accent).clickable(onClickLabel = stringResource(R.string.library_continue_game, rom.title), role = Role.Button) { a.play(rom, true, null) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Play, Color.White, 18.dp, filled = true) }
            }
        }
        item { Spacer(BottomSpace) }
    } }
}

/** The discreet engine credit next to the app's name. */
@Composable
private fun PoweredByRetroArch() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.library_powered_by) + " RetroArch", style = Type.Caption.copy(fontSize = 11.sp, color = Oe.Faint))
        androidx.compose.foundation.Image(
            painterResource(R.drawable.ic_retroarch), contentDescription = null, modifier = Modifier.size(14.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Oe.Faint),
        )
    }
}

/** Makes a sheet or dialog over the list modal for the screen buttons: they would act on the list behind it. */
@Composable
private fun ModalPadButtons() {
    val swallow = {}
    GamepadButtons(
        hints = listOf("A" to stringResource(R.string.settings_hint_choose), "B" to stringResource(R.string.detail_cancel)),
        l1 = swallow, r1 = swallow, x = swallow, y = swallow, select = swallow, start = swallow, l2 = swallow, r2 = swallow,
    )
}

/** A game's options from the list (Y): what the detail's three-dot menu offers, for the focused game. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameOptionsSheet(rom: Rom, onDismiss: () -> Unit, onShare: () -> Unit, onRemove: () -> Unit) {
    OeSheet(onDismiss) {
        SheetBarIcons()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(rom.title, style = Type.Title.copy(fontSize = 22.sp))
            Text(rom.console.displayName, style = Type.Small, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            Card {
                ListRow(stringResource(R.string.detail_share), leading = { Icon(Icons.Share, Oe.AccentText, 20.dp) }, onClick = onShare)
                ListRow(stringResource(R.string.detail_remove_from_library), divider = true, onClick = onRemove)
            }
        }
    }
}

/** Keeps [held] on this game while it holds focus, for the A hint. */
private fun Modifier.holdsGame(key: String, held: MutableState<String?>): Modifier = onFocusChanged {
    if (it.hasFocus || it.isFocused) held.value = key else if (held.value == key) held.value = null
}
