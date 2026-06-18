package dev.bleu.locallink.ui

import android.net.Uri
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.bleu.locallink.data.model.MediaCategory
import dev.bleu.locallink.data.model.MediaFile
import dev.bleu.locallink.ui.screens.*
import dev.bleu.locallink.viewmodel.MainViewModel

/**
 * Graf navigasi untuk seluruh aplikasi.
 *
 * Menggunakan Jetpack Compose Navigation dengan arsitektur single-Activity.
 * Semua screen menerima [MainViewModel] melalui shared `viewModel()` yang di-scope ke Activity.
 *
 * Rute screen:
 * - **Beranda** (discovery) → **PilihFile** (file picker) → **ProgresPengiriman** (progres kirim)
 * - **FileManager** → **MediaViewer** → **ProgresPengiriman**
 * - **KonfirmasiPenerimaan** (handshake masuk) → **ProgresPenerimaan** (progres terima)
 * - **RiwayatTransfer** (riwayat) — daftar transfer sebelumnya (read-only)
 * - **Pengaturan** (settings) — nama device, path penyimpanan, toggle auto-accept
 *
 * Transfer masuk dipicu otomatis melalui StateFlow [MainViewModel.incomingSender].
 */
sealed class Screen(val route: String) {
    object Discovery    : Screen("discovery")
    object FilePicker   : Screen("file_picker/{deviceId}") {
        fun createRoute(deviceId: String) = "file_picker/$deviceId"
    }
    object FileManager  : Screen("file_manager")
    object MediaViewer  : Screen("media_viewer/{encodedUri}/{name}/{sizeBytes}/{mimeType}/{category}") {
        fun createRoute(file: MediaFile): String {
            val enc  = Uri.encode(file.uri.toString())
            val name = Uri.encode(file.name)
            return "media_viewer/$enc/${name}/${file.sizeBytes}/${Uri.encode(file.mimeType)}/${file.category.name}"
        }
    }
    object SendProgress   : Screen("send_progress")
    object ReceiveConfirm : Screen("receive_confirm")
    object ReceiveProgress: Screen("receive_progress")
    object History        : Screen("history")
    object Settings       : Screen("settings")
}

@Composable
fun LocalLinkApp() {
    val navController = rememberNavController()
    val vm: MainViewModel = viewModel()

    val incomingSender by vm.incomingSender.collectAsState()
    val autoAccept     by vm.autoAccept.collectAsState()
    val hasSeenWelcome by vm.hasSeenWelcome.collectAsState()

    LaunchedEffect(hasSeenWelcome) { /* handled inside Discovery */ }

    LaunchedEffect(incomingSender) {
        if (incomingSender != null) {
            if (autoAccept) navController.navigate(Screen.ReceiveProgress.route)
            else navController.navigate(Screen.ReceiveConfirm.route)
        }
    }

    NavHost(navController = navController, startDestination = Screen.Discovery.route) {

        composable(Screen.Discovery.route) {
            BerandaScreen(
                vm = vm,
                onDeviceTapped = { device ->
                    vm.selectDevice(device)
                    navController.navigate(Screen.FilePicker.createRoute(device.id))
                },
                onFilesClick    = { navController.navigate(Screen.FileManager.route) },
                onHistoryClick  = { navController.navigate(Screen.History.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(
            route = Screen.FilePicker.route,
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) {
            PilihFileScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onSend = { navController.navigate(Screen.SendProgress.route) },
                onHistoryClick  = { navController.navigate(Screen.History.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.FileManager.route) {
            FileManagerScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onFileOpen = { file ->
                    navController.navigate(Screen.MediaViewer.createRoute(file))
                },
                onHistoryClick  = { navController.navigate(Screen.History.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(
            route = Screen.MediaViewer.route,
            arguments = listOf(
                navArgument("encodedUri")  { type = NavType.StringType },
                navArgument("name")        { type = NavType.StringType },
                navArgument("sizeBytes")   { type = NavType.LongType },
                navArgument("mimeType")    { type = NavType.StringType },
                navArgument("category")    { type = NavType.StringType }
            )
        ) { back ->
            val encodedUri = back.arguments?.getString("encodedUri") ?: ""
            val name       = back.arguments?.getString("name") ?: ""
            val sizeBytes  = back.arguments?.getLong("sizeBytes") ?: 0L
            val mimeType   = back.arguments?.getString("mimeType") ?: ""
            val category   = MediaCategory.valueOf(
                back.arguments?.getString("category") ?: MediaCategory.IMAGES.name
            )
            val file = MediaFile(
                uri           = Uri.parse(Uri.decode(encodedUri)),
                name          = Uri.decode(name),
                sizeBytes     = sizeBytes,
                mimeType      = Uri.decode(mimeType),
                dateModifiedMs = 0L,
                category      = category
            )
            MediaViewerScreen(
                vm   = vm,
                file = file,
                onBack          = { navController.popBackStack() },
                onSendNavigate  = { navController.navigate(Screen.SendProgress.route) }
            )
        }

        composable(Screen.SendProgress.route) {
            ProgresPengirimanScreen(
                vm = vm,
                onCancel        = { navController.popBackStack(Screen.Discovery.route, false) },
                onHistoryClick  = { navController.navigate(Screen.History.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.ReceiveConfirm.route) {
            KonfirmasiPenerimaanScreen(
                vm = vm,
                onAccept        = { navController.navigate(Screen.ReceiveProgress.route) },
                onReject        = {
                    vm.clearIncomingTransfer()
                    navController.popBackStack(Screen.Discovery.route, false)
                },
                onHistoryClick  = { navController.navigate(Screen.History.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.ReceiveProgress.route) {
            ProgresPenerimaanScreen(
                vm = vm,
                onCancel        = {
                    vm.clearIncomingTransfer()
                    navController.popBackStack(Screen.Discovery.route, false)
                },
                onHistoryClick  = { navController.navigate(Screen.History.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.History.route) {
            RiwayatTransferScreen(
                vm = vm,
                onTransferClick = { navController.navigate(Screen.Discovery.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Settings.route) {
            PengaturanScreen(
                vm = vm,
                onHistoryClick  = { navController.navigate(Screen.History.route) },
                onTransferClick = { navController.navigate(Screen.Discovery.route) }
            )
        }
    }
}
