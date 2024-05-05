package io.github.snd_r.komelia.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.snd_r.komelia.platform.cursorForHand
import io.github.snd_r.komelia.ui.common.CheckboxWithLabel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfirmationDialog(
    title: String,
    body: String,
    confirmText: String? = null,
    buttonCancel: String = "Cancel",
    buttonConfirm: String = "Confirm",
    buttonAlternate: String? = null,
    buttonConfirmColor: Color = MaterialTheme.colorScheme.secondary,
    onDialogConfirm: () -> Unit,
    onDialogConfirmAlternate: () -> Unit = {},
    onDialogDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = onDialogDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.surfaceVariant),
            modifier = modifier,
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(title, fontSize = 20.sp, modifier = Modifier.padding(vertical = 10.dp))
                Text(body, modifier = Modifier.padding(20.dp))

                var confirmed by remember { mutableStateOf(false) }
                if (confirmText != null) {
                    CheckboxWithLabel(
                        checked = confirmed,
                        onCheckedChange = { confirmed = it },
                        label = { Text(confirmText) }
                    )
                }

                FlowRow {
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = onDialogDismiss,
                        shape = RoundedCornerShape(5.dp),
                        modifier = Modifier.cursorForHand(),
                    ) {
                        Text(buttonCancel)
                    }
                    Spacer(Modifier.size(10.dp))

                    if (buttonAlternate != null) {
                        TextButton(
                            onClick = {
                                onDialogConfirmAlternate()
                                onDialogDismiss()
                            },
                            shape = RoundedCornerShape(5.dp),
                            modifier = Modifier.cursorForHand(),
                        ) {
                            Text(buttonAlternate)
                        }
                        Spacer(Modifier.size(10.dp))
                    }

                    FilledTonalButton(
                        onClick = {
                            onDialogConfirm()
                            onDialogDismiss()
                        },
                        enabled = confirmText == null || confirmed,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = buttonConfirmColor,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(5.dp),
                        modifier = Modifier.cursorForHand(),
                    ) {
                        Text(buttonConfirm)
                    }
                }
            }
        }

    }
}