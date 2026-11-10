package com.example.ebf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ebf.ui.library.LibraryScreen
import com.example.ebf.ui.library.LibraryViewModel
import com.example.ebf.ui.reader.ReaderScreen
import com.example.ebf.ui.theme.EBFTheme

class MainActivity : ComponentActivity() {

    private val libraryViewModel: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            EBFTheme {
                EbfNavigation(libraryViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** "Öffnen mit → EBF" aus einer anderen App. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { libraryViewModel.import(it) }
        }
    }
}

private object Routes {
    const val LIBRARY = "library"
    const val READER = "reader/{uri}"
    fun reader(bookUri: String) = "reader/" + Uri.encode(bookUri)
}

@Composable
private fun EbfNavigation(libraryViewModel: LibraryViewModel) {
    val navController = rememberNavController()

    LaunchedEffect(libraryViewModel) {
        libraryViewModel.openRequests.collect { bookUri ->
            navController.navigate(Routes.reader(bookUri)) {
                popUpTo(Routes.LIBRARY)
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                vm = libraryViewModel,
                onOpenBook = { navController.navigate(Routes.reader(it)) },
            )
        }
        composable(
            route = Routes.READER,
            arguments = listOf(navArgument("uri") { type = NavType.StringType }),
        ) { entry ->
            val bookUri = entry.arguments?.getString("uri").orEmpty()
            ReaderScreen(bookUri = bookUri, onBack = { navController.popBackStack() })
        }
    }
}
