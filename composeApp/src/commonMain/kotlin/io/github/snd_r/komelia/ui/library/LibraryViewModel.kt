package io.github.snd_r.komelia.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.snd_r.komelia.AppNotifications
import io.github.snd_r.komelia.ui.LoadState
import io.github.snd_r.komelia.ui.LoadState.Error
import io.github.snd_r.komelia.ui.LoadState.Loading
import io.github.snd_r.komelia.ui.LoadState.Success
import io.github.snd_r.komelia.ui.LoadState.Uninitialized
import io.github.snd_r.komelia.ui.common.menus.LibraryMenuActions
import io.github.snd_r.komelia.ui.library.LibraryTab.BROWSE
import io.github.snd_r.komelia.ui.library.LibraryTab.COLLECTIONS
import io.github.snd_r.komelia.ui.library.LibraryTab.READ_LISTS
import io.github.snd_r.komelia.ui.library.LibraryTab.RECOMMENDED
import io.github.snd_r.komga.collection.KomgaCollectionClient
import io.github.snd_r.komga.common.KomgaPageRequest
import io.github.snd_r.komga.library.KomgaLibrary
import io.github.snd_r.komga.library.KomgaLibraryClient
import io.github.snd_r.komga.readlist.KomgaReadListClient
import io.github.snd_r.komga.sse.KomgaEvent
import io.github.snd_r.komga.sse.KomgaEvent.CollectionAdded
import io.github.snd_r.komga.sse.KomgaEvent.CollectionDeleted
import io.github.snd_r.komga.sse.KomgaEvent.ReadListAdded
import io.github.snd_r.komga.sse.KomgaEvent.ReadListDeleted
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class LibraryViewModel(
    val library: StateFlow<KomgaLibrary>?,
    private val libraryClient: KomgaLibraryClient,
    private val collectionClient: KomgaCollectionClient,
    private val readListsClient: KomgaReadListClient,
    private val appNotifications: AppNotifications,
    private val komgaEvents: SharedFlow<KomgaEvent>,
) : StateScreenModel<LoadState<Unit>>(Uninitialized) {

    var currentTab by mutableStateOf(BROWSE)

    var collectionsCount by mutableStateOf(0)
        private set
    var readListsCount by mutableStateOf(0)
        private set

    private val reloadJobsFlow = MutableSharedFlow<Unit>(0, 1, DROP_OLDEST)

    fun initialize() {
        if (state.value !is Uninitialized) return

        screenModelScope.launch { loadItemCounts() }

        reloadJobsFlow.onEach {
            loadItemCounts()
            delay(1000)
        }.launchIn(screenModelScope)

        screenModelScope.launch { startEventListener() }

    }

    fun reload() {
        mutableState.value = Loading
        screenModelScope.launch { loadItemCounts() }
    }

    private suspend fun loadItemCounts() {
        if (state.value is Error) return

        appNotifications.runCatchingToNotifications {
            mutableState.value = Loading
            val pageRequest = KomgaPageRequest(size = 0)
            val libraryIds = library?.value?.let { listOf(it.id) } ?: emptyList()
            collectionsCount = collectionClient.getAll(libraryIds = libraryIds, pageRequest = pageRequest).totalElements
            readListsCount = readListsClient.getAll(libraryIds = libraryIds, pageRequest = pageRequest).totalElements

            if (collectionsCount == 0 && currentTab == COLLECTIONS) currentTab = BROWSE
            if (readListsCount == 0 && currentTab == READ_LISTS) currentTab = BROWSE
            mutableState.value = Success(Unit)
        }.onFailure { mutableState.value = Error(it) }
    }

    fun toRecommendedTab() {
        if (library == null) return
        currentTab = RECOMMENDED
    }

    fun toBrowseTab() {
        currentTab = BROWSE
    }

    fun toCollectionsTab() {
        currentTab = COLLECTIONS
    }

    fun toReadListsTab() {
        currentTab = READ_LISTS
    }

    fun libraryActions() = LibraryMenuActions(libraryClient, appNotifications, screenModelScope)

    private suspend fun startEventListener() {
        komgaEvents.collect { event ->
            when (event) {
                is ReadListAdded, is ReadListDeleted -> reloadJobsFlow.tryEmit(Unit)
                is CollectionAdded, is CollectionDeleted -> reloadJobsFlow.tryEmit(Unit)

                else -> {}
            }
        }
    }
}

enum class LibraryTab {
    BROWSE,
    RECOMMENDED,
    COLLECTIONS,
    READ_LISTS
}
