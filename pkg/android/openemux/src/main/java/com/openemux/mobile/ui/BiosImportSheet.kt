package com.openemux.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openemux.mobile.data.BiosImportReport
import com.openemux.mobile.data.biosNamesFor
import com.retroarch.R

/** What a per-console BIOS import did: the files it took, the ones it did not and why, and the names the console expects. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiosImportSheet(report: BiosImportReport, onClose: () -> Unit) {
    val console = report.console
    val done = remember { PadFocusTarget(initial = true) }
    OeSheet(onClose) {
        SheetBarIcons()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ConsoleIcon(console)
                Column {
                    Text(
                        when {
                            report.allImported -> countString(R.plurals.settings_bios_result_all, report.imported.size, report.imported.size)
                            report.noneImported -> stringResource(R.string.settings_bios_result_none)
                            else -> countString(R.plurals.settings_bios_result_some, report.total, report.imported.size, report.total)
                        },
                        style = Type.Title.copy(fontSize = 20.sp),
                    )
                    Text(console.fullName, style = Type.Small)
                }
            }
            if (report.imported.isNotEmpty()) {
                val imported = stringResource(R.string.settings_bios_section_imported)
                Section(imported) {
                    report.imported.forEachIndexed { i, file ->
                        val notes = buildList {
                            if (file.savedAs != file.source) add(stringResource(R.string.settings_bios_saved_as, file.savedAs))
                            if (file.byContent) add(stringResource(R.string.settings_bios_by_content))
                            if (file.replaced) add(stringResource(R.string.settings_bios_replaced))
                        }
                        ListRow(
                            file.source, subtitle = notes.joinToString(" · ").ifEmpty { null }, divider = i > 0, minHeight = 52.dp, subtitleLines = 3,
                            leading = { Icon(Icons.Check, Oe.Ok, 18.dp, stroke = 2.4f, modifier = Modifier.semantics { contentDescription = imported }) },
                        )
                    }
                }
            }
            if (report.rejected.isNotEmpty()) {
                val rejected = stringResource(R.string.settings_bios_section_rejected)
                Section(rejected) {
                    report.rejected.forEachIndexed { i, file ->
                        if (i > 0) HorizontalDivider(color = Oe.Divider, thickness = 1.dp)
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Alert, Oe.Warn, 18.dp, stroke = 2.4f, modifier = Modifier.semantics { contentDescription = rejected })
                            Column(Modifier.weight(1f)) {
                                Text(file.source, style = Type.Body)
                                Text(stringResource(reasonText(file.reason)), style = Type.Small.copy(color = Oe.WarnText))
                            }
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.settings_bios_expects, console.fullName), style = Type.Section.copy(fontSize = 13.sp, color = Oe.Muted))
                Text(
                    biosNamesFor(console).values.joinToString(" · "),
                    style = Type.Small.copy(lineHeight = 19.sp),
                )
            }
            PrimaryButton(stringResource(R.string.settings_bios_done), onClose, Modifier.padTarget(done).fillMaxWidth())
        }
    }
}

@Composable
private fun Section(title: String, rows: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = Type.Section.copy(fontSize = 13.sp, color = Oe.Muted))
        Card(Modifier.border(1.dp, Oe.Border, RoundedCornerShape(18.dp))) { rows() }
    }
}

internal fun reasonText(reason: BiosImportReport.Reason): Int = when (reason) {
    BiosImportReport.Reason.WRONG_NAME -> R.string.settings_bios_reason_name
    BiosImportReport.Reason.IO -> R.string.settings_bios_reason_io
    BiosImportReport.Reason.TOO_LARGE -> R.string.settings_bios_reason_size
}
