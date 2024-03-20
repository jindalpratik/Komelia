package io.github.snd_r.komelia.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.snd_r.komelia.ui.LoadState.Error
import io.github.snd_r.komelia.ui.LoadState.Loading
import io.github.snd_r.komelia.ui.LoadState.Uninitialized
import io.github.snd_r.komelia.ui.LocalViewModelFactory
import io.github.snd_r.komelia.ui.book.BookScreen
import io.github.snd_r.komelia.ui.collection.CollectionScreen
import io.github.snd_r.komelia.ui.common.ErrorContent
import io.github.snd_r.komelia.ui.common.LoadingMaxSizeIndicator
import io.github.snd_r.komelia.ui.common.menus.LibraryActionsMenu
import io.github.snd_r.komelia.ui.common.menus.LibraryMenuActions
import io.github.snd_r.komelia.ui.library.LibraryTab.BROWSE
import io.github.snd_r.komelia.ui.library.LibraryTab.COLLECTIONS
import io.github.snd_r.komelia.ui.library.LibraryTab.READ_LISTS
import io.github.snd_r.komelia.ui.library.LibraryTab.RECOMMENDED
import io.github.snd_r.komelia.ui.library.view.DashboardContent
import io.github.snd_r.komelia.ui.library.view.LibraryCollectionsContent
import io.github.snd_r.komelia.ui.library.view.LibraryReadListsContent
import io.github.snd_r.komelia.ui.reader.view.ReaderScreen
import io.github.snd_r.komelia.ui.readlist.ReadListScreen
import io.github.snd_r.komelia.ui.series.SeriesScreen
import io.github.snd_r.komelia.ui.series.view.SeriesListContent
import io.github.snd_r.komga.library.KomgaLibrary
import io.github.snd_r.komga.library.KomgaLibraryId

class LibraryScreen(val libraryId: KomgaLibraryId? = null) : Screen {

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel(libraryId?.value) {
            viewModelFactory.getLibraryViewModel(libraryId)
        }
        LaunchedEffect(libraryId) { vm.initialize() }

        when (val state = vm.state.collectAsState().value) {
            is Error -> ErrorContent(
                message = state.exception.message ?: "Unknown Error",
                onReload = vm::reload
            )

            else -> {
                Column {
                    LibraryToolBar(
                        library = vm.library?.value,
                        currentTab = vm.currentTab,
                        libraryActions = vm.libraryActions(),
                        collectionsCount = vm.collectionsCount,
                        readListsCount = vm.readListsCount,
                        onRecommendedClick = vm::toRecommendedTab,
                        onBrowseClick = vm::toBrowseTab,
                        onCollectionsClick = vm::toCollectionsTab,
                        onReadListsClick = vm::toReadListsTab
                    )
                    when (vm.currentTab) {
                        BROWSE -> BrowseTab()
                        RECOMMENDED -> RecommendedTab()
                        COLLECTIONS -> CollectionsTab()
                        READ_LISTS -> ReadListsTab()
                    }
                }

            }
        }

    }

    @Composable
    private fun BrowseTab() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel("browse_${libraryId?.value}") {
            viewModelFactory.getSeriesBrowseViewModel(libraryId)
        }

        val navigator = LocalNavigator.currentOrThrow
        LaunchedEffect(libraryId) { vm.initialize() }
        when (val state = vm.state.collectAsState().value) {
            Uninitialized -> LoadingMaxSizeIndicator()
            is Error -> ErrorContent(
                message = state.exception.message ?: "Unknown Error",
                onReload = vm::reload
            )

            else -> {
                val loading = state is Loading
                SeriesListContent(
                    series = vm.series,
                    seriesActions = vm.seriesMenuActions(),
                    seriesTotalCount = vm.totalSeriesCount,
                    onSeriesClick = { navigator.push(SeriesScreen(it)) },
                    isLoading = loading,

                    sortOrder = vm.sortOrder,
                    onSortOrderChange = vm::onSortOrderChange,

                    currentPage = vm.currentSeriesPage,
                    totalPages = vm.totalSeriesPages,
                    pageSize = vm.pageLoadSize,
                    onPageSizeChange = vm::onPageSizeChange,
                    onPageChange = vm::onPageChange,

                    minSize = vm.cardWidth.collectAsState().value,
                )
            }
        }
    }

    @Composable
    private fun RecommendedTab() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel("recommended_${libraryId?.value}") {
            viewModelFactory.getLibraryRecommendationViewModel(libraryId)
        }
        val navigator = LocalNavigator.currentOrThrow
        LaunchedEffect(libraryId) { vm.initialize() }

        when (val state = vm.state.collectAsState().value) {
            is Error -> ErrorContent(
                message = state.exception.message ?: "Unknown Error",
                onReload = vm::reload
            )

            else -> DashboardContent(
                keepReadingBooks = vm.keepReadingBooks,
                recentlyReleasedBooks = vm.recentlyReleasedBooks,
                recentlyAddedBooks = vm.recentlyAddedBooks,
                recentlyAddedSeries = vm.recentlyAddedSeries,
                recentlyUpdatedSeries = vm.recentlyUpdatedSeries,
                cardWidth = vm.cardWidth.collectAsState().value,

                onSeriesClick = { navigator push SeriesScreen(it) },
                seriesMenuActions = vm.seriesMenuActions(),
                bookMenuActions = vm.bookMenuActions(),
                onBookClick = { navigator push BookScreen(it) },
                onBookReadClick = { navigator.parent?.replace(ReaderScreen(it)) },
            )
        }
    }

    @Composable
    private fun CollectionsTab() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel("collections_${libraryId?.value}") {
            viewModelFactory.getLibraryCollectionsViewModel(libraryId)
        }

        val navigator = LocalNavigator.currentOrThrow
        LaunchedEffect(libraryId) { vm.initialize() }

        when (val state = vm.state.collectAsState().value) {

            Uninitialized -> LoadingMaxSizeIndicator()
            is Error -> ErrorContent(
                message = state.exception.message ?: "Unknown Error",
                onReload = vm::reload
            )

            else -> {
                val loading = state is Loading
                LibraryCollectionsContent(
                    collections = vm.collections,
                    collectionsTotalCount = vm.totalCollections,
                    onCollectionClick = { navigator push CollectionScreen(it) },
                    onCollectionDelete = vm::onCollectionDelete,
                    isLoading = loading,

                    totalPages = vm.totalPages,
                    currentPage = vm.currentPage,
                    pageSize = vm.pageSize,
                    onPageChange = vm::onPageChange,
                    onPageSizeChange = vm::onPageSizeChange,

                    minSize = vm.cardWidth.collectAsState().value
                )

            }
        }

    }

    @Composable
    private fun ReadListsTab() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel("readLists_${libraryId?.value}") {
            viewModelFactory.getLibraryReadListsViewModel(libraryId)
        }
        LaunchedEffect(libraryId) { vm.initialize() }
        val navigator = LocalNavigator.currentOrThrow

        when (val state = vm.state.collectAsState().value) {
            Uninitialized -> LoadingMaxSizeIndicator()
            is Error -> Text("Error")
            else -> {
                val loading = state is Loading
                LibraryReadListsContent(
                    readLists = vm.readLists,
                    readListsTotalCount = vm.totalReadLists,
                    onReadListClick = { navigator push ReadListScreen(it) },
                    onReadListDelete = vm::onReadListDelete,
                    isLoading = loading,

                    totalPages = vm.totalPages,
                    currentPage = vm.currentPage,
                    pageSize = vm.pageSize,
                    onPageChange = vm::onPageChange,
                    onPageSizeChange = vm::onPageSizeChange,

                    minSize = vm.cardWidth.collectAsState().value
                )
            }
        }
    }

}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryToolBar(
    library: KomgaLibrary?,
    currentTab: LibraryTab,
    libraryActions: LibraryMenuActions,
    collectionsCount: Int,
    readListsCount: Int,
    onRecommendedClick: () -> Unit,
    onBrowseClick: () -> Unit,
    onCollectionsClick: () -> Unit,
    onReadListsClick: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.Center,
    ) {

        val title = library?.let { library.name } ?: "All Libraries"
        Text(title, Modifier.align(Alignment.CenterVertically))

        var showOptionsMenu by remember { mutableStateOf(false) }
        if (library != null) {
            Box {
                IconButton(
                    onClick = { showOptionsMenu = true }
                ) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = null,
                    )
                }

                LibraryActionsMenu(
                    library = library,
                    actions = libraryActions,
                    expanded = showOptionsMenu,
                    onDismissRequest = { showOptionsMenu = false }
                )
            }
        }

        Spacer(Modifier.width(30.dp))

        val chipColors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )

        if (library != null) {
            FilterChip(
                onClick = onRecommendedClick,
                selected = currentTab == RECOMMENDED,
                label = { Text("RECOMMENDED") },
                colors = chipColors,
                border = null,
            )
        }

        FilterChip(
            onClick = onBrowseClick,
            selected = currentTab == BROWSE,
            label = { Text("BROWSE") },
            colors = chipColors,
            border = null,
        )

        if (collectionsCount > 0)
            FilterChip(
                onClick = onCollectionsClick,
                selected = currentTab == COLLECTIONS,
                label = { Text("COLLECTIONS") },
                colors = chipColors,
                border = null,
            )

        if (readListsCount > 0)
            FilterChip(
                onClick = onReadListsClick,
                selected = currentTab == READ_LISTS,
                label = { Text("READ LISTS") },
                colors = chipColors,
                border = null,
            )

    }
}