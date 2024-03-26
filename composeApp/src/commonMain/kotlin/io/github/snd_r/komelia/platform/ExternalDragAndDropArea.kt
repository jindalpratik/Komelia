package io.github.snd_r.komelia.platform

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.nio.file.Path


@Composable
expect fun ExternalDragAndDropArea(
    onFileUpload: (List<Path>) -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit
)