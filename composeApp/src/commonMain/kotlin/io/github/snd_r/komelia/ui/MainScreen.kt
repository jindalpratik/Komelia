package io.github.snd_r.komelia.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DrawerValue.Closed
import androidx.compose.material3.DrawerValue.Open
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import io.github.snd_r.komelia.platform.WindowWidth
import io.github.snd_r.komelia.platform.WindowWidth.FULL
import io.github.snd_r.komelia.ui.book.BookScreen
import io.github.snd_r.komelia.ui.home.HomeScreen
import io.github.snd_r.komelia.ui.library.LibraryScreen
import io.github.snd_r.komelia.ui.navigation.AppBar
import io.github.snd_r.komelia.ui.navigation.NavBarContent
import io.github.snd_r.komelia.ui.search.SearchScreen
import io.github.snd_r.komelia.ui.series.SeriesScreen
import io.github.snd_r.komelia.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

class MainScreen(
    private val defaultScreen: Screen = HomeScreen()
) : Screen {

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val width = LocalWindowWidth.current

        Navigator(
            screen = defaultScreen,
            onBackPressed = null,
        ) { navigator ->
            val vm = rememberScreenModel { viewModelFactory.getNavigationViewModel(navigator) }

            LaunchedEffect(width) {
                when (width) {
                    FULL -> vm.navBarState.snapTo(Open)
                    else -> vm.navBarState.snapTo(Closed)
                }
            }

            Column {
                AppBar(
                    onMenuButtonPress = { vm.toggleNavBar() },
                    query = vm.searchBarState.currentQuery(),
                    onQueryChange = vm.searchBarState::onQueryChange,
                    isLoading = vm.searchBarState.isLoading,
                    onSearchAllClick = { navigator.push(SearchScreen(it)) },
                    searchResults = vm.searchBarState.searchResults(),
                    libraryById = vm.searchBarState::getLibraryById,
                    onBookClick = { navigator.replaceAll(BookScreen(it)) },
                    onSeriesClick = { navigator.replaceAll(SeriesScreen(it)) },
                )

                when (width) {
                    // is not working properly https://issuetracker.google.com/issues/218286829#comment6
//                    FULL -> DismissibleNavigationDrawer(
//                        drawerState = vm.navBarState,
//                        drawerContent = { NavBar(vm, navigator) },
//                        content = { CurrentScreen() }
//                    )
                    FULL -> Row {
                        if (vm.navBarState.targetValue == Open) NavBar(vm, navigator, width)
                        CurrentScreen()
                    }

                    else -> ModalNavigationDrawer(
                        drawerState = vm.navBarState,
                        drawerContent = { NavBar(vm, navigator, width) },
                        content = { CurrentScreen() }
                    )


                }
            }
        }

    }

    @Composable
    private fun NavBar(
        vm: MainScreenViewModel,
        navigator: Navigator,
        width: WindowWidth
    ) {
        val coroutineScope = rememberCoroutineScope()
        NavBarContent(
            currentScreen = navigator.lastItem,
            libraries = vm.libraries.collectAsState().value,
            libraryActions = vm.getLibraryActions(),
            onHomeClick = {
                navigator.replaceAll(HomeScreen())
                if (width != FULL) coroutineScope.launch { vm.navBarState.snapTo(Closed) }
            },
            onLibrariesClick = {
                navigator.replaceAll(LibraryScreen())
                if (width != FULL) coroutineScope.launch { vm.navBarState.snapTo(Closed) }
            },

            onLibraryClick = {
                navigator.replaceAll(LibraryScreen(it))
                if (width != FULL) coroutineScope.launch { vm.navBarState.snapTo(Closed) }
            },
            onSettingsClick = { navigator.parent!!.push(SettingsScreen()) },
            taskQueueStatus = vm.komgaTaskQueueStatus.collectAsState().value
        )
    }
}
