package io.github.intealma.skyddsrum.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.vector.ImageVector
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

    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var showCityPicker by remember { mutableStateOf(false) }
    /** Whether choosing a city should immediately open the closest shelter (the one-tap flow). */
    var openClosestAfterPick by remember { mutableStateOf(false) }
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
    fun findNearest() {
        openClosestAfterPick = true
        val current = origin
        if (!vm.hasLocationPermission() && current != null && !current.isDeviceLocation) {
            // Location was declined earlier and a municipality is chosen: use it directly.
            vm.openClosest()
            return
        }
        withLocationPermission { vm.locate(openClosest = true, onFailed = { showCityPicker = true }) }
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
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.label)) },
                    )
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
                onChangeOrigin = { openClosestAfterPick = false; showCityPicker = true },
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
                        hasLocationPermission = hasPermission,
                        onShelterTap = { vm.select(it) },
                        onMyLocation = { if (!hasPermission) withLocationPermission { } },
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

    selected?.let { item ->
        ShelterDetailSheet(
            item = item,
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
                vm.chooseMunicipality(place, openClosest = openClosestAfterPick)
            },
            onUseMyLocation = {
                showCityPicker = false
                withLocationPermission { vm.locate(openClosest = openClosestAfterPick, onFailed = { showCityPicker = true }) }
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
