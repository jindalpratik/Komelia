package io.github.snd_r.komelia.ui.dialogs.collectionedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.common.CheckboxWithLabel
import io.github.snd_r.komelia.ui.dialogs.tabs.DialogTab
import io.github.snd_r.komelia.ui.dialogs.tabs.TabItem

internal class GeneralTab(
    private val vm: CollectionEditDialogViewModel,
) : DialogTab {

    override fun options() = TabItem(
        title = "GENERAL",
        icon = Icons.Default.FormatAlignCenter
    )

    @Composable
    override fun Content() {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            TextField(
                value = vm.name,
                onValueChange = vm::name::set,
                label = { Text("Name") },
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()
            Column {
                Text(
                    "By default, series in a collection will be ordered by name. You can enable manual ordering to define your own order.",
                    style = MaterialTheme.typography.bodyMedium
                )
                CheckboxWithLabel(
                    checked = vm.manualOrdering,
                    onCheckedChange = vm::manualOrdering::set,
                    label = { Text("Manual ordering") }
                )

            }
        }
    }

}