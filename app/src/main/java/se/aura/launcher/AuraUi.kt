package se.aura.launcher

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private val AuraBlack = Color(0xFF07090B)
private val AuraPanel = Color(0xFF15191D)
private val AuraIvory = Color(0xFFF3EEE6)
private val AuraText = Color(0xFFF6F6F4)
private val AuraMuted = Color(0xFF9A9EA3)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuraLauncher(
    apps: List<AuraApp>,
    launchApp: (AuraApp) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE) }

    var drawerVisible by rememberSaveable { mutableStateOf(false) }
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    var animations by rememberSaveable { mutableStateOf(prefs.getBoolean("animations", true)) }
    var iconSize by rememberSaveable { mutableFloatStateOf(prefs.getFloat("icon_size", 58f)) }
    var hidden by remember {
        mutableStateOf(prefs.getStringSet("hidden_apps", emptySet())?.toSet().orEmpty())
    }
    var pinned by remember {
        mutableStateOf(prefs.getStringSet("pinned_apps", emptySet())?.toSet().orEmpty())
    }

    val visibleApps = remember(apps, hidden) { apps.filterNot { it.packageName in hidden } }
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val transitionMs = if (animations) 300 else 0

    fun saveHidden(value: Set<String>) {
        hidden = value
        prefs.edit().putStringSet("hidden_apps", value).apply()
    }

    fun savePinned(value: Set<String>) {
        pinned = value
        prefs.edit().putStringSet("pinned_apps", value).apply()
    }

    BackHandler(drawerVisible || settingsVisible) {
        when {
            settingsVisible -> settingsVisible = false
            drawerVisible -> drawerVisible = false
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color.Transparent,
            surface = AuraPanel,
            primary = AuraIvory,
            onPrimary = Color.Black
        )
    ) {
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !drawerVisible && !settingsVisible,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> FocusPage(animations = animations)
                    1 -> HomePage(
                        apps = visibleApps,
                        pinned = pinned,
                        iconSize = iconSize,
                        launchApp = launchApp,
                        openDrawer = { drawerVisible = true },
                        openSettings = { settingsVisible = true }
                    )
                    2 -> WidgetsPage(apps = visibleApps, launchApp = launchApp)
                }
            }

            AnimatedVisibility(
                visible = drawerVisible,
                enter = fadeIn(tween(transitionMs)) + slideInVertically(tween(transitionMs)) { it / 5 },
                exit = fadeOut(tween(transitionMs)) + slideOutVertically(tween(transitionMs)) { it / 5 }
            ) {
                AppDrawer(
                    apps = visibleApps,
                    pinned = pinned,
                    iconSize = iconSize,
                    launchApp = launchApp,
                    onPin = { pkg ->
                        savePinned(if (pkg in pinned) pinned - pkg else pinned + pkg)
                    },
                    onHide = { pkg ->
                        saveHidden(hidden + pkg)
                        savePinned(pinned - pkg)
                    },
                    openSettings = {
                        drawerVisible = false
                        settingsVisible = true
                    },
                    close = { drawerVisible = false }
                )
            }

            AnimatedVisibility(
                visible = settingsVisible,
                enter = fadeIn(tween(transitionMs)) + slideInVertically(tween(transitionMs)) { it / 4 },
                exit = fadeOut(tween(transitionMs)) + slideOutVertically(tween(transitionMs)) { it / 4 }
            ) {
                SettingsPage(
                    animations = animations,
                    iconSize = iconSize,
                    hiddenCount = hidden.size,
                    onAnimationsChange = {
                        animations = it
                        prefs.edit().putBoolean("animations", it).apply()
                    },
                    onIconSizeChange = {
                        iconSize = it
                        prefs.edit().putFloat("icon_size", it).apply()
                    },
                    resetHidden = { saveHidden(emptySet()) },
                    close = { settingsVisible = false }
                )
            }
        }
    }
}

@Composable
private fun HomePage(
    apps: List<AuraApp>,
    pinned: Set<String>,
    iconSize: Float,
    launchApp: (AuraApp) -> Unit,
    openDrawer: () -> Unit,
    openSettings: () -> Unit
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(15_000)
        }
    }

    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(now) }
    val date = remember(now) {
        SimpleDateFormat("EEE d MMM", Locale("sv", "SE"))
            .format(now)
            .replaceFirstChar { it.uppercase() }
    }

    val pinnedApps = remember(apps, pinned) { apps.filter { it.packageName in pinned }.take(4) }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0x33000000),
                        Color(0x16000000),
                        Color(0x9E000000)
                    )
                )
            )
            .pointerInput(openDrawer) {
                var vertical = 0f
                detectVerticalDragGestures(
                    onDragStart = { vertical = 0f },
                    onVerticalDrag = { _, amount -> vertical += amount },
                    onDragEnd = {
                        if (vertical < -120f) openDrawer()
                        vertical = 0f
                    },
                    onDragCancel = { vertical = 0f }
                )
            }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 14.dp, top = 42.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "A U R A",
                color = Color.White.copy(.58f),
                fontSize = 10.sp,
                letterSpacing = 4.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = openSettings) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "Inställningar",
                    tint = Color.White.copy(.55f)
                )
            }
        }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 145.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                time,
                color = Color.White,
                fontSize = 64.sp,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = (-2).sp
            )
            Text(
                date,
                color = Color.White.copy(.72f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Light
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (pinnedApps.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    pinnedApps.forEach { app ->
                        AppShortcut(app, iconSize = iconSize - 4f, launchApp = launchApp)
                    }
                }
            }

            Dock(
                apps = apps,
                iconSize = iconSize,
                launchApp = launchApp,
                context = context
            )

            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .width(42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(.75f))
            )
        }
    }
}

@Composable
private fun Dock(
    apps: List<AuraApp>,
    iconSize: Float,
    launchApp: (AuraApp) -> Unit,
    context: Context
) {
    val phone = remember(apps) { findApp(apps, listOf("com.google.android.dialer", "dialer")) }
    val messages = remember(apps) { findApp(apps, listOf("com.google.android.apps.messaging", "messaging")) }
    val chrome = remember(apps) { findApp(apps, listOf("com.android.chrome", "chrome")) }
    val camera = remember(apps) { findApp(apps, listOf("com.google.android.GoogleCamera", "camera")) }

    Surface(
        color = Color.Black.copy(.34f),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(.08f))
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DockSlot(
                app = phone,
                fallbackIcon = Icons.Outlined.Phone,
                iconSize = iconSize,
                onFallback = { safeStart(context, Intent(Intent.ACTION_DIAL)) },
                launchApp = launchApp
            )
            DockSlot(
                app = messages,
                fallbackIcon = Icons.Outlined.Message,
                iconSize = iconSize,
                onFallback = { safeStart(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))) },
                launchApp = launchApp
            )
            DockSlot(
                app = chrome,
                fallbackIcon = Icons.Outlined.Language,
                iconSize = iconSize,
                onFallback = {
                    safeStart(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
                },
                launchApp = launchApp
            )
            DockSlot(
                app = camera,
                fallbackIcon = Icons.Outlined.CameraAlt,
                iconSize = iconSize,
                onFallback = {
                    safeStart(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                },
                launchApp = launchApp
            )
        }
    }
}

@Composable
private fun DockSlot(
    app: AuraApp?,
    fallbackIcon: ImageVector,
    iconSize: Float,
    onFallback: () -> Unit,
    launchApp: (AuraApp) -> Unit
) {
    Box(
        Modifier
            .size((iconSize + 8).dp)
            .clip(CircleShape)
            .background(Color.White.copy(.08f))
            .clickable { if (app != null) launchApp(app) else onFallback() },
        contentAlignment = Alignment.Center
    ) {
        if (app != null) {
            AppImage(app, iconSize.dp)
        } else {
            Icon(fallbackIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppDrawer(
    apps: List<AuraApp>,
    pinned: Set<String>,
    iconSize: Float,
    launchApp: (AuraApp) -> Unit,
    onPin: (String) -> Unit,
    onHide: (String) -> Unit,
    openSettings: () -> Unit,
    close: () -> Unit
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<AuraApp?>(null) }
    val gridState = rememberLazyGridState()

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFC0D1115), Color(0xFF06080A))
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp)
        ) {
            Spacer(Modifier.height(42.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Appar",
                    color = AuraText,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = openSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Inställningar", tint = AuraMuted)
                }
                IconButton(onClick = close) {
                    Icon(Icons.Outlined.Close, contentDescription = "Stäng", tint = AuraText)
                }
            }

            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Sök appar…", color = AuraMuted) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = AuraMuted) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(.08f),
                    unfocusedContainerColor = Color.White.copy(.08f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = AuraIvory,
                    focusedTextColor = AuraText,
                    unfocusedTextColor = AuraText
                )
            )

            Spacer(Modifier.height(20.dp))

            Box(Modifier.weight(1f)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    gridItems(filtered, key = { it.packageName + it.activityName }) { app ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.combinedClickable(
                                onClick = { launchApp(app) },
                                onLongClick = { selected = app }
                            )
                        ) {
                            AppImage(app, iconSize.dp)
                            Spacer(Modifier.height(7.dp))
                            Text(
                                app.label,
                                color = AuraText.copy(.78f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (app.packageName in pinned) {
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(AuraIvory)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { app ->
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            containerColor = Color(0xFF11151A),
            contentColor = AuraText
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppImage(app, 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(app.label, fontSize = 20.sp)
                        Text(app.packageName, color = AuraMuted, fontSize = 11.sp)
                    }
                }

                Spacer(Modifier.height(18.dp))

                DrawerAction(
                    icon = Icons.Outlined.PushPin,
                    title = if (app.packageName in pinned) "Ta bort från hemskärmen" else "Fäst på hemskärmen"
                ) {
                    onPin(app.packageName)
                    selected = null
                }

                DrawerAction(Icons.Outlined.Visibility, "Dölj app") {
                    onHide(app.packageName)
                    selected = null
                }

                DrawerAction(Icons.Outlined.Info, "Appinformation") {
                    safeStart(
                        context,
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${app.packageName}")
                        )
                    )
                    selected = null
                }

                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun DrawerAction(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = AuraText.copy(.82f))
        Spacer(Modifier.width(14.dp))
        Text(title, color = AuraText, fontSize = 15.sp)
    }
}

@Composable
private fun WidgetsPage(
    apps: List<AuraApp>,
    launchApp: (AuraApp) -> Unit
) {
    val context = LocalContext.current
    val now = remember { Date() }
    val greeting = remember {
        val hour = SimpleDateFormat("H", Locale.getDefault()).format(now).toIntOrNull() ?: 12
        when (hour) {
            in 5..10 -> "God morgon"
            in 11..16 -> "God dag"
            in 17..22 -> "God kväll"
            else -> "Hej"
        }
    }
    val dateDay = SimpleDateFormat("d", Locale("sv", "SE")).format(now)
    val dateMonth = SimpleDateFormat("MMM", Locale("sv", "SE")).format(now)

    var calendarGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var events by remember { mutableStateOf<List<AuraEvent>>(emptyList()) }

    val calendarPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        calendarGranted = granted
    }

    LaunchedEffect(calendarGranted) {
        events = if (calendarGranted) loadUpcomingEvents(context) else emptyList()
    }

    val spotify = remember(apps) { findApp(apps, listOf("com.spotify.music", "spotify")) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF11161B), AuraBlack)))
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(54.dp))
            Text(greeting, color = AuraText, fontSize = 29.sp, fontWeight = FontWeight.Light)
            Text("Information när du behöver den.", color = AuraMuted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PremiumCard(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WbSunny, contentDescription = null, tint = Color(0xFFFFC85A))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Väder", color = AuraText, fontSize = 18.sp)
                            Text("Öppna prognos", color = AuraMuted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    TextButton(
                        onClick = {
                            safeStart(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=weather")))
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = AuraIvory)
                    ) { Text("Visa väder") }
                }

                PremiumCard(Modifier.width(96.dp)) {
                    Text("Idag", color = AuraMuted, fontSize = 11.sp)
                    Text(dateDay, color = AuraText, fontSize = 36.sp, fontWeight = FontWeight.Light)
                    Text(dateMonth, color = AuraText.copy(.7f), fontSize = 13.sp)
                }
            }
        }

        item {
            PremiumCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Nästa", color = AuraMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = AuraMuted)
                }
                Spacer(Modifier.height(10.dp))

                if (!calendarGranted) {
                    Text("Anslut kalendern för att visa dina kommande aktiviteter.", color = AuraText.copy(.72f), fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) }) {
                        Text("Tillåt kalender")
                    }
                } else if (events.isEmpty()) {
                    Text("Inga kommande kalenderhändelser.", color = AuraMuted, fontSize = 13.sp)
                } else {
                    events.take(3).forEach { event ->
                        EventRow(event)
                    }
                }
            }
        }

        item {
            PremiumCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(.08f),
                        modifier = Modifier.size(58.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = AuraText)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Musik", color = AuraText, fontSize = 17.sp)
                        Text(if (spotify != null) "Spotify" else "Öppna musikapp", color = AuraMuted, fontSize = 12.sp)
                    }
                    IconButton(
                        onClick = {
                            if (spotify != null) launchApp(spotify)
                            else safeStart(context, Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER))
                        }
                    ) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = AuraText)
                    }
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickSystem(Icons.Outlined.Wifi, "Internet") {
                    safeStart(context, Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                }
                QuickSystem(Icons.Outlined.Bluetooth, "Bluetooth") {
                    safeStart(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }
                QuickSystem(Icons.Outlined.NotificationsOff, "Stör ej") {
                    safeStart(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
                QuickSystem(Icons.Outlined.DisplaySettings, "Skärm") {
                    safeStart(context, Intent(Settings.ACTION_DISPLAY_SETTINGS))
                }
            }
        }

        item { Spacer(Modifier.height(34.dp)) }
    }
}

@Composable
private fun EventRow(event: AuraEvent) {
    val time = remember(event.beginMillis) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(event.beginMillis))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(30.dp)
                .clip(CircleShape)
                .background(Color(0xFFE6A74A))
        )
        Spacer(Modifier.width(10.dp))
        Text(time, color = AuraText.copy(.72f), fontSize = 12.sp, modifier = Modifier.width(48.dp))
        Column {
            Text(event.title, color = AuraText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!event.location.isNullOrBlank()) {
                Text(event.location, color = AuraMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun QuickSystem(icon: ImageVector, label: String, action: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier
                .size(54.dp)
                .clickable(onClick = action),
            shape = CircleShape,
            color = Color.White.copy(.075f),
            border = BorderStroke(1.dp, Color.White.copy(.05f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = AuraText.copy(.86f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = AuraMuted, fontSize = 9.sp)
    }
}

data class FocusMode(
    val name: String,
    val time: String,
    val icon: ImageVector
)

@Composable
private fun FocusPage(animations: Boolean) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE) }
    val modes = remember {
        mutableStateListOf(
            FocusMode("Arbete", "08:00 – 16:00", Icons.Outlined.WorkOutline),
            FocusMode("Träning", "17:00 – 19:00", Icons.Outlined.DirectionsRun),
            FocusMode("Hemmaläge", "19:00 – 23:00", Icons.Outlined.Home),
            FocusMode("Stör ej", "Aktivera manuellt", Icons.Outlined.DarkMode)
        )
    }
    var active by rememberSaveable { mutableStateOf(prefs.getString("focus_mode", "Arbete") ?: "Arbete") }
    var addDialog by rememberSaveable { mutableStateOf(false) }
    var customName by rememberSaveable { mutableStateOf("") }

    val notificationManager = remember {
        context.getSystemService(NotificationManager::class.java)
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF101419), AuraBlack)))
            .padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(56.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Fokus", color = AuraText, fontSize = 31.sp, fontWeight = FontWeight.Light)
                    Text("Mindre brus. Mer närvaro.", color = AuraMuted, fontSize = 13.sp)
                }
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { addDialog = true },
                    shape = CircleShape,
                    color = Color.White.copy(.07f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("+", color = AuraText, fontSize = 28.sp, fontWeight = FontWeight.Light)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        items(modes, key = { it.name }) { mode ->
            val isActive = mode.name == active
            val target = if (isActive) AuraIvory else Color.White.copy(.065f)
            val bg by animateColorAsState(targetValue = target, animationSpec = tween(if (animations) 240 else 0))
            val scale by animateFloatAsState(
                targetValue = if (isActive) 1f else .985f,
                animationSpec = tween(if (animations) 240 else 0)
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clickable {
                        if (mode.name == "Stör ej") {
                            if (!notificationManager.isNotificationPolicyAccessGranted) {
                                safeStart(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                            } else {
                                val enable = active != "Stör ej"
                                notificationManager.setInterruptionFilter(
                                    if (enable) NotificationManager.INTERRUPTION_FILTER_NONE
                                    else NotificationManager.INTERRUPTION_FILTER_ALL
                                )
                                active = if (enable) "Stör ej" else "Hemmaläge"
                                prefs.edit().putString("focus_mode", active).apply()
                            }
                        } else {
                            active = mode.name
                            prefs.edit().putString("focus_mode", active).apply()
                            if (
                                notificationManager.isNotificationPolicyAccessGranted &&
                                notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE
                            ) {
                                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                            }
                        }
                    },
                shape = RoundedCornerShape(26.dp),
                color = bg,
                border = if (isActive) null else BorderStroke(1.dp, Color.White.copy(.05f))
            ) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        mode.icon,
                        contentDescription = null,
                        tint = if (isActive) Color.Black else AuraText
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            mode.name,
                            color = if (isActive) Color.Black else AuraText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            mode.time,
                            color = if (isActive) Color.Black.copy(.58f) else AuraMuted,
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = if (isActive) Color.Black.copy(.65f) else AuraMuted
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(20.dp))
            Text(
                "Svep åt vänster för hemskärmen",
                color = AuraMuted.copy(.62f),
                fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(30.dp))
        }
    }

    if (addDialog) {
        AlertDialog(
            onDismissRequest = { addDialog = false },
            containerColor = Color(0xFF15191D),
            title = { Text("Nytt fokusläge") },
            text = {
                TextField(
                    value = customName,
                    onValueChange = { customName = it },
                    singleLine = true,
                    placeholder = { Text("Namn") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = customName.trim()
                        if (name.isNotEmpty() && modes.none { it.name.equals(name, true) }) {
                            modes += FocusMode(name, "Manuellt", Icons.Outlined.CenterFocusStrong)
                            active = name
                            prefs.edit().putString("focus_mode", name).apply()
                        }
                        customName = ""
                        addDialog = false
                    }
                ) { Text("Lägg till") }
            },
            dismissButton = {
                TextButton(onClick = { addDialog = false }) { Text("Avbryt") }
            }
        )
    }
}

@Composable
private fun SettingsPage(
    animations: Boolean,
    iconSize: Float,
    hiddenCount: Int,
    onAnimationsChange: (Boolean) -> Unit,
    onIconSizeChange: (Float) -> Unit,
    resetHidden: () -> Unit,
    close: () -> Unit
) {
    val context = LocalContext.current

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF11151A), AuraBlack)))
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                Spacer(Modifier.height(48.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Anpassa", color = AuraText, fontSize = 30.sp, fontWeight = FontWeight.Light)
                        Text("Aura Launcher 2.0", color = AuraMuted, fontSize = 12.sp)
                    }
                    IconButton(onClick = close) {
                        Icon(Icons.Outlined.Close, contentDescription = "Stäng", tint = AuraText)
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            item {
                SettingsAction(Icons.Outlined.Home, "Standardlauncher", "Välj vilken hemskärm Android ska använda") {
                    safeStart(context, Intent(Settings.ACTION_HOME_SETTINGS))
                }
            }

            item {
                SettingsAction(Icons.Outlined.Wallpaper, "Bakgrund", "Använd Androids vanliga bakgrundsväljare") {
                    safeStart(context, Intent(Intent.ACTION_SET_WALLPAPER))
                }
            }

            item {
                SettingsToggle(
                    icon = Icons.Outlined.Palette,
                    title = "Animationer",
                    subtitle = "Mjuka sidbyten och övergångar",
                    checked = animations,
                    onCheckedChange = onAnimationsChange
                )
            }

            item {
                PremiumCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Apps, contentDescription = null, tint = AuraText)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Ikonstorlek", color = AuraText, fontSize = 15.sp)
                            Text("${iconSize.toInt()} dp", color = AuraMuted, fontSize = 11.sp)
                        }
                    }
                    Slider(
                        value = iconSize,
                        onValueChange = onIconSizeChange,
                        valueRange = 48f..68f
                    )
                }
            }

            item {
                SettingsAction(
                    Icons.Outlined.Visibility,
                    "Dolda appar",
                    if (hiddenCount == 0) "Inga appar är dolda" else "$hiddenCount dolda • tryck för att återställa"
                ) {
                    if (hiddenCount > 0) resetHidden()
                }
            }

            item {
                SettingsAction(Icons.Outlined.Lock, "Integritet", "Ingen spårning och inga annons-SDK:er") { }
            }

            item {
                PremiumCard {
                    Text("Gester", color = AuraText, fontSize = 16.sp)
                    Spacer(Modifier.height(10.dp))
                    GestureRow("↑", "Svep upp", "Applåda")
                    GestureRow("←", "Svep vänster", "Widgets")
                    GestureRow("→", "Svep höger", "Fokus")
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun SettingsAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = AuraText.copy(.78f))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = AuraText, fontSize = 15.sp)
            Text(subtitle, color = AuraMuted, fontSize = 11.sp)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = AuraMuted.copy(.6f))
    }
    HorizontalDivider(color = Color.White.copy(.055f))
}

@Composable
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = AuraText.copy(.78f))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = AuraText, fontSize = 15.sp)
            Text(subtitle, color = AuraMuted, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
    HorizontalDivider(color = Color.White.copy(.055f))
}

@Composable
private fun GestureRow(symbol: String, gesture: String, action: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(symbol, color = AuraIvory, fontSize = 20.sp, modifier = Modifier.width(28.dp))
        Text(gesture, color = AuraText.copy(.8f), fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(action, color = AuraMuted, fontSize = 12.sp)
    }
}

@Composable
private fun PremiumCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(.065f),
        border = BorderStroke(1.dp, Color.White.copy(.055f))
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun AppShortcut(
    app: AuraApp,
    iconSize: Float,
    launchApp: (AuraApp) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { launchApp(app) }
    ) {
        AppImage(app, iconSize.dp)
        Spacer(Modifier.height(5.dp))
        Text(
            app.label,
            color = Color.White.copy(.72f),
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AppImage(app: AuraApp, size: androidx.compose.ui.unit.Dp) {
    val pm = LocalContext.current.packageManager
    val bitmap = remember(app.packageName, app.activityName, size) {
        app.info.loadIcon(pm)
            .toBitmap(
                size.value.toInt().coerceAtLeast(48) * 2,
                size.value.toInt().coerceAtLeast(48) * 2
            )
            .asImageBitmap()
    }

    Image(
        bitmap = bitmap,
        contentDescription = app.label,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * .28f))
    )
}

private fun findApp(apps: List<AuraApp>, hints: List<String>): AuraApp? {
    return apps.firstOrNull { app ->
        hints.any { hint ->
            app.packageName.equals(hint, ignoreCase = true) ||
                app.packageName.contains(hint, ignoreCase = true)
        }
    }
}

private fun safeStart(context: Context, intent: Intent) {
    runCatching {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
