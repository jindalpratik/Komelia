package io.github.snd_r.komelia.ui.settings.announcements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.platform.cursorForHand
import io.github.snd_r.komga.announcements.KomgaJsonFeed.KomgaAnnouncement
import org.jsoup.Jsoup

@Composable
fun AnnouncementsContent(announcements: List<KomgaAnnouncement>) {
    Column(verticalArrangement = Arrangement.spacedBy(50.dp)) {
        announcements.forEach {
            Announcement(it)
            HorizontalDivider()
        }
    }
}

@Composable
private fun Announcement(announcement: KomgaAnnouncement) {
    Column {
        announcement.title?.let { title ->
            AnnouncementTitle(title, announcement.url)
        }

        announcement.dateModified?.let {
            Text(it.toString(), modifier = Modifier.padding(vertical = 20.dp))
        }

        announcement.contentHtml?.let {
            AnnouncementBody(it)
        }


    }
}

@Composable
private fun AnnouncementTitle(title: String, url: String?) {
    val uriHandler = LocalUriHandler.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered = interactionSource.collectIsHoveredAsState()

    val onClickModifier = url?.let {
        Modifier
            .clickable(interactionSource = interactionSource, indication = null) { uriHandler.openUri(it) }
            .hoverable(interactionSource)
            .cursorForHand()
    } ?: Modifier

    val style =
        if (isHovered.value)
            MaterialTheme.typography.headlineLarge.copy(textDecoration = TextDecoration.Underline)
        else
            MaterialTheme.typography.headlineLarge

    Text(title, style = style, modifier = onClickModifier)
}

@Composable
private fun AnnouncementBody(contentHtml: String) {
    val formattedBody = remember(contentHtml) {
        Jsoup.parse(contentHtml).wholeText()
    }

    Text(formattedBody)
}