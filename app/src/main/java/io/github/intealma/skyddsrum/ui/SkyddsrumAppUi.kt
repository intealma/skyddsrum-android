package io.github.intealma.skyddsrum.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.models.StoreTransaction
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialog
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialogOptions
import com.revenuecat.purchases.ui.revenuecatui.PaywallListener
import io.github.intealma.skyddsrum.R
import io.github.intealma.skyddsrum.premium.Premium
import io.github.intealma.skyddsrum.ui.map.MapScreen
import io.github.intealma.skyddsrum.ui.map.SWEDEN_CENTER
import io.github.intealma.skyddsrum.ui.theme.Black
import io.github.intealma.skyddsrum.ui.theme.Line
import io.github.intealma.skyddsrum.ui.theme.TextFaint
import io.github.intealma.skyddsrum.ui.theme.White
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

enum class Tab(val label: Int, val icon: ImageVector) {
    HOME(R.string.tab_home, Icons.Filled.Home),
    MAP(R.string.tab_map, Icons.Filled.Place),
    PLACES(R.string.tab_places, Icons.Filled.Star),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings),
}

/** Why the paywall is open: a premium feature (only shown if not yet premium) or "Support the project". */
private enum class PaywallReason { FEATURE, SUPPORT }

@Composable
fun SkyddsrumAppUi(vm: AppViewModel = viewModel()) {
    var showIntro by rememberSaveable { mutableStateOf(true) }
    Box(Modifier.fillMaxSize()) {
        AppContent(vm)
        if (showIntro) IntroScreen(onFinished = { showIntro = false })
    }
}

@Composable
private fun AppContent(vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val shelters by vm.shelters.collectAsStateWithLifecycle()
    val municipalities by vm.municipalities.collectAsStateWithLifecycle()
    val origin by vm.origin.collectAsStateWithLifecycle()
    val locating by vm.locating.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val nearest by vm.nearest.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val highlightedId by vm.highlightedId.collectAsStateWithLifecycle()
    val mapFocus by vm.mapFocus.collectAsStateWithLifecycle()
    val isPremium by vm.isPremium.collectAsStateWithLifecycle()
    val savedPlaces by vm.savedPlaces.collectAsStateWithLifecycle()
    val routes by vm.routes.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var showCityPicker by remember { mutableStateOf(false) }
    /** Whether choosing a city should immediately route to the best shelters (the one-tap flow). */
    var routesAfterPick by remember { mutableStateOf(false) }
    var paywall by remember { mutableStateOf<PaywallReason?>(null) }
    var restoring by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(vm.hasLocationPermission()) }
    /** What to do once the permission dialog returns with a grant. */
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasPermission = result.values.any { it }
        val next = afterPermission
        afterPermission = null
        if (hasPermission) {
            next?.invoke()
        } else {
            vm.onPermissionDenied()
            showCityPicker = true
        }
    }
    fun withLocationPermission(action: () -> Unit) {
        if (vm.hasLocationPermission()) {
            hasPermission = true
            action()
        } else {
            afterPermission = action
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    /** One tap: locate, then show the 3 best shelters with routes on the map (as on the website). */
    fun findNearest() {
        routesAfterPick = true
        tab = Tab.MAP
        val current = origin
        if (!vm.hasLocationPermission() && current != null && !current.isDeviceLocation) {
            // Location was declined earlier and a municipality is chosen: use it directly.
            vm.showRoutes()
            return
        }
        withLocationPermission { vm.locate(showRoutes = true, onFailed = { showCityPicker = true }) }
    }
    fun openPremiumFeature() {
        if (!isPremium) paywall = PaywallReason.FEATURE
    }

    // A purchase made anywhere (paywall, restore, renewal) closes a feature paywall and unlocks live.
    LaunchedEffect(isPremium) { if (isPremium && paywall == PaywallReason.FEATURE) paywall = null }

    BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                HorizontalDivider(color = Line)
                NavigationBar(containerColor = Black, tonalElevation = 0.dp) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
                            label = { Text(stringResource(t.label).uppercase(), style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = White,
                                selectedTextColor = White,
                                unselectedIconColor = TextFaint,
                                unselectedTextColor = TextFaint,
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (tab) {
            Tab.HOME -> HomeScreen(
                shelterCount = shelters?.size,
                origin = origin,
                locating = locating,
                message = message,
                nearest = nearest,
                onFindNearest = ::findNearest,
                onChangeOrigin = { routesAfterPick = false; showCityPicker = true },
                onOpen = vm::select,
                modifier = contentModifier,
            )
            Tab.MAP -> {
                val list = shelters
                if (list == null) {
                    Box(contentModifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    MapScreen(
                        shelters = list,
                        origin = origin,
                        focus = mapFocus,
                        selectedId = highlightedId,
                        routes = routes,
                        locating = locating,
                        onShelterTap = { vm.select(it) },
                        onLocate = { if (routes != null) vm.clearRoutes() else findNearest() },
                        onModeChange = { vm.setTravelMode(it) },
                        onRouteTap = vm::select,
                        onCloseRoutes = vm::clearRoutes,
                        onFocusConsumed = vm::onMapFocusConsumed,
                        modifier = contentModifier,
                    )
                }
            }
            Tab.PLACES -> SavedPlacesScreen(
                isPremium = isPremium,
                places = savedPlaces,
                onUnlock = ::openPremiumFeature,
                onUseCurrentLocation = { slot ->
                    withLocationPermission {
                        vm.setSavedPlaceToCurrentLocation(slot) {
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.location_unavailable)) }
                        }
                    }
                },
                onSetOnMap = vm::setSavedPlace,
                onClear = vm::clearSavedPlace,
                onOpen = vm::select,
                initialMapCenter = origin?.let { GeoPoint(it.place.lat, it.place.lon) } ?: SWEDEN_CENTER,
                modifier = contentModifier,
            )
            Tab.SETTINGS -> SettingsScreen(
                isPremium = isPremium,
                restoring = restoring,
                onSupport = { paywall = PaywallReason.SUPPORT },
                onRestore = {
                    if (!Premium.isConfigured) {
                        paywall = PaywallReason.SUPPORT // shows the "not configured" explanation
                        return@SettingsScreen
                    }
                    restoring = true
                    scope.launch {
                        val text = try {
                            context.getString(if (Premium.restore()) R.string.restore_premium else R.string.restore_none)
                        } catch (e: Exception) {
                            context.getString(R.string.restore_failed, e.message ?: "")
                        }
                        restoring = false
                        snackbar.showSnackbar(text)
                    }
                },
                modifier = contentModifier,
            )
        }
    }

    selected?.let { selection ->
        ShelterDetailSheet(
            selection = selection,
            onShowOnMap = { shelter ->
                vm.focusMapOn(shelter)
                vm.dismissSelected()
                tab = Tab.MAP
            },
            onDismiss = vm::dismissSelected,
        )
    }

    if (showCityPicker) {
        CityPickerDialog(
            municipalities = municipalities,
            onPick = { place ->
                showCityPicker = false
                vm.chooseMunicipality(place, showRoutes = routesAfterPick)
            },
            onUseMyLocation = {
                showCityPicker = false
                withLocationPermission { vm.locate(showRoutes = routesAfterPick, onFailed = { showCityPicker = true }) }
            },
            onDismiss = { showCityPicker = false },
        )
    }

    paywall?.let { reason ->
        if (!Premium.isConfigured) {
            AlertDialog(
                onDismissRequest = { paywall = null },
                title = { Text(stringResource(R.string.rc_not_configured_title)) },
                text = { Text(stringResource(R.string.rc_not_configured)) },
                confirmButton = { TextButton(onClick = { paywall = null }) { Text(stringResource(R.string.ok)) } },
            )
        } else {
            PaywallDialog(
                PaywallDialogOptions.Builder()
                    .apply { if (reason == PaywallReason.FEATURE) setRequiredEntitlementIdentifier(Premium.ENTITLEMENT_ID) }
                    .setDismissRequest { paywall = null }
                    .setListener(object : PaywallListener {
                        override fun onPurchaseCompleted(customerInfo: CustomerInfo, storeTransaction: StoreTransaction) {
                            Premium.update(customerInfo)
                            paywall = null
                        }
                        override fun onRestoreCompleted(customerInfo: CustomerInfo) {
                            Premium.update(customerInfo)
                        }
                        override fun onPurchaseError(error: PurchasesError) {
                            scope.launch { snackbar.showSnackbar(error.message) }
                        }
                    })
                    .build(),
            )
        }
    }
}
