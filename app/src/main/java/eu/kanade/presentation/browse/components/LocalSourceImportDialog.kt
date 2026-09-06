package eu.kanade.presentation.browse.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun LocalSourceImportDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var mangaName by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = mangaName.isNotBlank(),
                onClick = { onConfirm(mangaName.trim()) },
            ) {
                Text(text = stringResource(MR.strings.action_choose_files))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.action_import_local_manga))
        },
        text = {
            OutlinedTextField(
                modifier = Modifier.focusRequester(focusRequester),
                value = mangaName,
                onValueChange = { mangaName = it },
                label = { Text(text = stringResource(MR.strings.local_source_import_manga_name)) },
                supportingText = {
                    Text(text = stringResource(MR.strings.local_source_import_description))
                },
                singleLine = true,
            )
        },
    )

    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }
}
