package io.github.snd_r.komelia.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.snd_r.komelia.AppNotifications
import io.github.snd_r.komelia.ui.LoadState
import io.github.snd_r.komelia.ui.LoadState.Uninitialized
import io.github.snd_r.komelia.ui.common.cards.defaultCardWidth
import io.github.snd_r.komelia.ui.common.menus.BookMenuActions
import io.github.snd_r.komelia.ui.common.menus.SeriesMenuActions
import io.github.snd_r.komga.book.KomgaBook
import io.github.snd_r.komga.book.KomgaBookClient
import io.github.snd_r.komga.book.KomgaBookQuery
import io.github.snd_r.komga.book.KomgaBooksSort
import io.github.snd_r.komga.book.KomgaReadStatus
import io.github.snd_r.komga.common.KomgaPageRequest
import io.github.snd_r.komga.series.KomgaSeries
import io.github.snd_r.komga.series.KomgaSeriesClient
import io.github.snd_r.komga.sse.KomgaEvent
import io.github.snd_r.komga.sse.KomgaEvent.BookEvent
import io.github.snd_r.komga.sse.KomgaEvent.ReadProgressEvent
import io.github.snd_r.komga.sse.KomgaEvent.ReadProgressSeriesEvent
import io.github.snd_r.komga.sse.KomgaEvent.SeriesEvent
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit.Companion.MONTH
import kotlinx.datetime.TimeZone.Companion.UTC
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

class HomeViewModel(
    private val seriesClient: KomgaSeriesClient,
    private val bookClient: KomgaBookClient,
    private val appNotifications: AppNotifications,
    private val komgaEvents: SharedFlow<KomgaEvent>,
    cardWidthFlow: Flow<Dp>,
) : StateScreenModel<LoadState<Unit>>(Uninitialized) {
    val cardWidth = cardWidthFlow.stateIn(screenModelScope, Eagerly, defaultCardWidth.dp)

    var keepReadingBooks by mutableStateOf<List<KomgaBook>>(emptyList())
        private set
    var recentlyReleasedBooks = mutableStateListOf<KomgaBook>()
        private set
    var recentlyAddedBooks = mutableStateListOf<KomgaBook>()
        private set
    var recentlyAddedSeries = mutableStateListOf<KomgaSeries>()
        private set
    var recentlyUpdatedSeries = mutableStateListOf<KomgaSeries>()
        private set

    var activeFilter by mutableStateOf(HomeScreenFilter.ALL)
        private set

    private val reloadJobsFlow = MutableSharedFlow<Unit>(0, 1, DROP_OLDEST)

    fun initialize() {
        if (state.value !is Uninitialized) return

        screenModelScope.launch { startEventListener() }

        reloadJobsFlow.onEach {
            load()
            delay(5000)
        }.launchIn(screenModelScope)

        screenModelScope.launch { load() }
    }

    fun reload() {
        screenModelScope.launch { load() }
    }

    private suspend fun load() {
        appNotifications.runCatchingToNotifications {

            mutableState.value = LoadState.Loading
            loadKeepReadingBooks()
            loadRecentlyReleasedBooks()
            loadRecentlyAddedBooks()
            loadRecentlyAddedSeries()
            loadRecentlyUpdatedSeries()
            mutableState.value = LoadState.Success(Unit)

        }.onFailure { mutableState.value = LoadState.Error(it) }
    }

    fun seriesMenuActions() = SeriesMenuActions(seriesClient, appNotifications, screenModelScope)
    fun bookMenuActions() = BookMenuActions(bookClient, appNotifications, screenModelScope)

    private suspend fun loadKeepReadingBooks() {
        val pageRequest = KomgaPageRequest(sort = KomgaBooksSort.byReadDateDesc())

        val books = bookClient.getAllBooks(
            query = KomgaBookQuery(
                readStatus = listOf(KomgaReadStatus.IN_PROGRESS),
            ),
            pageRequest = pageRequest
        ).content

        keepReadingBooks = books
    }

    private suspend fun loadRecentlyReleasedBooks() {
        val pageRequest = KomgaPageRequest(sort = KomgaBooksSort.byReleaseDateDesc())

        val books = bookClient.getAllBooks(
            pageRequest = pageRequest,
            query = KomgaBookQuery(
                releasedAfter = Clock.System.todayIn(UTC).minus(1, MONTH)
            ),
        ).content

        recentlyReleasedBooks.clear()
        recentlyReleasedBooks.addAll(books)
    }

    private suspend fun loadRecentlyAddedBooks() {
        val pageRequest = KomgaPageRequest(sort = KomgaBooksSort.byCreatedDateDesc())

        val books = bookClient.getAllBooks(
            pageRequest = pageRequest,
        ).content

        recentlyAddedBooks.clear()
        recentlyAddedBooks.addAll(books)
    }

    private suspend fun loadRecentlyAddedSeries() {
        val series = seriesClient.getNewSeries(
            oneshot = false
        ).content
        recentlyAddedSeries.clear()
        recentlyAddedSeries.addAll(series)
    }

    private suspend fun loadRecentlyUpdatedSeries() {
        val series = seriesClient.getUpdatedSeries(
            oneshot = false
        ).content
        recentlyUpdatedSeries.clear()
        recentlyUpdatedSeries.addAll(series)
    }

    private suspend fun startEventListener() {
        komgaEvents.collect { event ->
            when (event) {
                is BookEvent -> {
                    reloadJobsFlow.tryEmit(Unit)
                }

                is SeriesEvent -> {
                    reloadJobsFlow.tryEmit(Unit)
                }

                is ReadProgressEvent -> {
                    val matches = keepReadingBooks.any { event.bookId == it.id }
                            || recentlyAddedBooks.any { event.bookId == it.id }
                            || recentlyAddedBooks.any { event.bookId == it.id }

                    if (matches) reloadJobsFlow.tryEmit(Unit)

                }

                is ReadProgressSeriesEvent -> {
                    val matches = recentlyAddedSeries.any { event.seriesId == it.id }
                            || recentlyUpdatedSeries.any { event.seriesId == it.id }

                    if (matches) reloadJobsFlow.tryEmit(Unit)
                }

                else -> {}
            }
        }
    }

    fun onFilterChange(filter: HomeScreenFilter) {
        this.activeFilter = filter
    }

    enum class HomeScreenFilter {
        ALL,
        KEEP_READING_BOOKS,
        RECENTLY_RELEASED_BOOKS,
        RECENTLY_ADDED_BOOKS,
        RECENTLY_ADDED_SERIES,
        RECENTLY_UPDATED_SERIES

    }
}

