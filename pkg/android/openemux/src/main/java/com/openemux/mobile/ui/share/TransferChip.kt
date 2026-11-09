package com.openemux.mobile.ui.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openemux.mobile.share.TransferNotice
import com.openemux.mobile.share.TransferSurface
import com.openemux.mobile.ui.Icon
import com.openemux.mobile.ui.Icons
import com.openemux.mobile.ui.InputMode
import com.openemux.mobile.ui.LocalActions
import com.openemux.mobile.ui.Oe
import com.openemux.mobile.ui.PadBadge
import com.openemux.mobile.ui.Type
import com.openemux.mobile.ui.toastPill
import com.retroarch.R

/** A transfer the player left running: what it is doing, and the way back to its progress view (a tap, or Start on a pad). */
@Composable
fun TransferChip(modifier: Modifier = Modifier) {
    val share = LocalActions.current.app.shareCoordinator
    if (rememberTransferSurface(share) != TransferSurface.CHIP) return
    val notice = TransferNotice.of(share.transfer.collectAsState().value) ?: return
    Row(
        modifier
            .toastPill()
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.share_chip_open), onClick = share::showProgress)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (notice.outgoing) Icons.Upload else Icons.Download, Oe.AccentText, 18.dp)
        Spacer(Modifier.width(10.dp))
        val label = stringResource(if (notice.outgoing) R.string.share_chip_sending else R.string.share_chip_receiving)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Oe.Text)) { append(label) }
                notice.percent?.let { withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = Oe.Muted)) { append(" · $it%") } }
            },
            style = Type.Body.copy(fontSize = 14.sp),
            maxLines = 1,
        )
        if (InputMode.gamepad) {
            Spacer(Modifier.width(10.dp))
            PadBadge("START")
        }
    }
}
