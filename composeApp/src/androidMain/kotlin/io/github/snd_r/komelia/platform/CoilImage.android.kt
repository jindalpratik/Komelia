package io.github.snd_r.komelia.platform

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil3.Image
import coil3.annotation.ExperimentalCoilApi

@OptIn(ExperimentalCoilApi::class)
@Composable
actual fun ReaderImage(image: Image) {
    val context = LocalContext.current
    Image(
        bitmap = image.asDrawable(context.resources).toBitmap().asImageBitmap(),
        contentDescription = null,
        filterQuality = FilterQuality.None
    )
}
