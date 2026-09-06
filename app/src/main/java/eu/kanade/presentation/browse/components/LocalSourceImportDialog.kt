package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun LocalSourceImportDialog(
    onDismissRequest: () -> Unit,
    onChooseFiles: () -> Unit,
    onChooseFolder: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.action_import_local_manga))
        },
        text = {
            Column {
                Text(text = stringResource(MR.strings.local_source_import_description))
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onChooseFiles,
                ) {
                    Text(text = stringResource(MR.strings.local_source_import_files))
                }
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onChooseFolder,
                ) {
                    Text(text = stringResource(MR.strings.local_source_import_folder))
                }
            }
        },
    )
}
