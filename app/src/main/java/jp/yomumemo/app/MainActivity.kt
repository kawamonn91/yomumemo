package jp.yomumemo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import jp.yomumemo.app.data.repo.NoteRepository
import jp.yomumemo.app.ui.book.BookDetailScreen
import jp.yomumemo.app.ui.book.BookDetailViewModel
import jp.yomumemo.app.ui.note.NoteEditorScreen
import jp.yomumemo.app.ui.note.NoteEditorViewModel
import jp.yomumemo.app.ui.paywall.PaywallScreen
import jp.yomumemo.app.ui.paywall.PaywallViewModel
import jp.yomumemo.app.ui.rememberAppContainer
import jp.yomumemo.app.ui.scan.ScanScreen
import jp.yomumemo.app.ui.scan.ScanViewModel
import jp.yomumemo.app.ui.search.SearchScreen
import jp.yomumemo.app.ui.search.SearchViewModel
import jp.yomumemo.app.ui.settings.SettingsScreen
import jp.yomumemo.app.ui.settings.SettingsViewModel
import jp.yomumemo.app.ui.stats.StatsScreen
import jp.yomumemo.app.ui.stats.StatsViewModel
import jp.yomumemo.app.ui.shelf.ShelfScreen
import jp.yomumemo.app.ui.shelf.ShelfViewModel
import jp.yomumemo.app.ui.theme.YomuMemoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            YomuMemoTheme {
                YomuMemoNavHost()
            }
        }
    }
}

private object Routes {
    const val SHELF = "shelf"
    const val SCAN = "scan"
    const val SEARCH = "search"
    const val PAYWALL = "paywall"
    const val SETTINGS = "settings"
    const val STATS = "stats"
    const val BOOK = "book/{bookId}"
    const val NOTE_NEW = "note/{bookId}"
    const val NOTE_EDIT = "note/{bookId}/{noteId}"

    fun book(id: String) = "book/$id"
    fun newNote(bookId: String) = "note/$bookId"
    fun editNote(bookId: String, noteId: String) = "note/$bookId/$noteId"
}

@Composable
private fun YomuMemoNavHost() {
    val navController = rememberNavController()
    val container = rememberAppContainer()

    // リポジトリはコンテナが保持する同じ実体を画面間で共有する
    val bookRepository = container.bookRepository
    val noteRepository = container.noteRepository

    NavHost(navController = navController, startDestination = Routes.SHELF) {

        composable(Routes.SHELF) {
            val vm: ShelfViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { ShelfViewModel(bookRepository) }
                },
            )
            ShelfScreen(
                viewModel = vm,
                onOpenBook = { navController.navigate(Routes.book(it)) },
                onScan = { navController.navigate(Routes.SCAN) },
                onSearch = { navController.navigate(Routes.SEARCH) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        SettingsViewModel(bookRepository, noteRepository, container.entitlements)
                    }
                },
            )
            SettingsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenPaywall = { navController.navigate(Routes.PAYWALL) },
                onOpenStats = { navController.navigate(Routes.STATS) },
            )
        }

        composable(Routes.STATS) {
            val vm: StatsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        StatsViewModel(bookRepository, noteRepository, container.entitlements)
                    }
                },
            )
            StatsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenPaywall = { navController.navigate(Routes.PAYWALL) },
            )
        }

        composable(Routes.SCAN) {
            val vm: ScanViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        ScanViewModel(container.bookLookup, bookRepository, container.entitlements)
                    }
                },
            )
            ScanScreen(
                viewModel = vm,
                onSaved = { bookId ->
                    navController.navigate(Routes.book(bookId)) {
                        popUpTo(Routes.SHELF)
                    }
                },
                onOpenPaywall = { navController.navigate(Routes.PAYWALL) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PAYWALL) {
            val vm: PaywallViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { PaywallViewModel(container.billing, container.entitlements) }
                },
            )
            PaywallScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.SEARCH) {
            val vm: SearchViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { SearchViewModel(noteRepository, bookRepository) }
                },
            )
            SearchScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenBook = { navController.navigate(Routes.book(it)) },
            )
        }

        composable(
            route = Routes.BOOK,
            arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
        ) { entry ->
            val bookId = entry.arguments?.getString("bookId").orEmpty()
            val vm: BookDetailViewModel = viewModel(
                key = "book-$bookId",
                factory = viewModelFactory {
                    initializer { BookDetailViewModel(bookId, bookRepository, noteRepository) }
                },
            )
            BookDetailScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onAddNote = { navController.navigate(Routes.newNote(bookId)) },
                onEditNote = { noteId -> navController.navigate(Routes.editNote(bookId, noteId)) },
            )
        }

        composable(
            route = Routes.NOTE_NEW,
            arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
        ) { entry ->
            NoteEditorRoute(
                bookId = entry.arguments?.getString("bookId").orEmpty(),
                noteId = null,
                notes = noteRepository,
                onDone = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.NOTE_EDIT,
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("noteId") { type = NavType.StringType },
            ),
        ) { entry ->
            NoteEditorRoute(
                bookId = entry.arguments?.getString("bookId").orEmpty(),
                noteId = entry.arguments?.getString("noteId"),
                notes = noteRepository,
                onDone = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun NoteEditorRoute(
    bookId: String,
    noteId: String?,
    notes: NoteRepository,
    onDone: () -> Unit,
) {
    val vm: NoteEditorViewModel = viewModel(
        key = "note-$bookId-$noteId",
        factory = viewModelFactory {
            initializer { NoteEditorViewModel(bookId, noteId, notes) }
        },
    )
    NoteEditorScreen(viewModel = vm, onDone = onDone)
}
