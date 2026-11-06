package com.openemux.mobile.ui.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.StoredSkin
import com.openemux.mobile.share.ResultKind
import com.openemux.mobile.share.SendPart
import com.openemux.mobile.share.ShareFormat
import com.openemux.mobile.share.ShareResult
import com.openemux.mobile.share.SkinToSend
import com.openemux.mobile.share.SkipNote
import com.openemux.mobile.ui.ConsoleChip
import com.openemux.mobile.ui.ListRow
import com.openemux.mobile.ui.LocalActions
import com.openemux.mobile.ui.Routes
import com.retroarch.R

/**
 * What "Share" does on one of the player's own skins of [console] (the skin list's actions sheet): hands the skin to
 * Share nearby and opens its screen, which sends that one skin to another Android device on the network.
 */
@Composable
fun rememberSkinShare(console: Console): (StoredSkin) -> Unit {
    val a = LocalActions.current
    return remember(a, console) {
        { skin ->
            a.app.shareCoordinator.startSendSkin(SkinToSend(console, skin.id, skin.name))
            a.nav.navigate(Routes.SHARE) { launchSingleTop = true }
        }
    }
}

/** The Share screen's "What to Send" for a skin: the one thing going, with nothing to check or uncheck. */
@Composable
fun SkinToSendRow(skin: SkinToSend, bytes: Long) {
    ListRow(skin.name, subtitle = skinSummary(bytes), minHeight = 60.dp, leading = { ConsoleChip(skin.console, 36.dp) })
}

/** "Controller Skin · 310 kB": a skin as a row says what it is, beside its console's chip. */
@Composable
fun skinSummary(bytes: Long): String = partName(SendPart.SKIN) + " · " + sizeText(bytes)

/**
 * The result card's title and body for a transfer of a controller skin, where a game's would not do: it went, it did
 * not, or it was declined. Null for the ends a game's text says as well (cancelled, expired, connection lost, failed).
 */
@Composable
fun skinResultText(result: ShareResult): Pair<String, String>? {
    val peer = result.peer
    val went = result.games > 0
    return when (result.kind) {
        ResultKind.RECEIVED -> if (went) {
            val console = result.firstConsole?.let { Console.byId(it)?.displayName ?: it }.orEmpty()
            stringResource(R.string.share_skin_result_received_title) to stringResource(R.string.share_skin_result_received_body, peer, console)
        } else {
            stringResource(R.string.share_skin_result_received_none) to stringResource(R.string.share_skin_result_received_none_body, peer)
        }
        ResultKind.SENT -> if (went) {
            stringResource(R.string.share_skin_result_sent_title) to stringResource(R.string.share_skin_result_sent_body, peer)
        } else {
            stringResource(R.string.share_skin_result_sent_none) to stringResource(R.string.share_skin_result_sent_none_body, peer)
        }
        ResultKind.DECLINED -> stringResource(R.string.share_result_declined_title) to stringResource(R.string.share_skin_result_declined_body, peer)
        else -> null
    }
}

/**
 * Why [peer] did not take the skin this device sent. An OpenEmux from before skins could be shared answers the kind
 * it does not know as a file it cannot place, so that reads as an app to update.
 */
@Composable
fun skinSentSkipText(reason: String?, peer: String): String = when (ShareFormat.reasonKey(reason)) {
    SkipNote.UNSUPPORTED_CONSOLE -> stringResource(R.string.share_reason_sent_unsupported, peer)
    SkipNote.USER_UNCHECKED -> stringResource(R.string.share_reason_sent_not_accepted, peer)
    else -> stringResource(R.string.share_skin_reason_sent, peer)
}
