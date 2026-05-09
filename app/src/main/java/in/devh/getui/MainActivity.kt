package `in`.devh.getui

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import `in`.devh.getui.data.DumpEntity
import `in`.devh.getui.data.DumpSummary
import `in`.devh.getui.ui.theme.GetUITheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("getui_prefs", MODE_PRIVATE)
        val isOnboardingCompleted = prefs.getBoolean("onboarding_completed", false)
        val isTileRequested = prefs.getBoolean("tile_requested", false)

        setContent {
            GetUITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val context = LocalContext.current
                    val viewModel: MainViewModel = viewModel()
                    
                    var isServiceEnabled by remember { mutableStateOf(false) }
                    var areNotificationsEnabled by remember { mutableStateOf(true) }

                    LaunchedEffect(Unit) {
                        while (true) {
                            isServiceEnabled = XmlDumpService.isRunning
                            areNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                            delay(1000)
                        }
                    }

                    val startDestination = when {
                        !isOnboardingCompleted -> "onboarding"
                        !isServiceEnabled -> "permissions"
                        !isTileRequested -> "tile_request"
                        else -> "list"
                    }

                    NavHost(
                        navController = navController, 
                        startDestination = startDestination
                    ) {
                        composable("onboarding") {
                            OnboardingScreen { 
                                prefs.edit().putBoolean("onboarding_completed", true).apply()
                                navController.navigate("permissions") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            }
                        }
                        composable("permissions") {
                            PermissionsScreen(
                                isServiceEnabled = isServiceEnabled,
                                areNotificationsEnabled = areNotificationsEnabled,
                                onNext = { 
                                    if (!isTileRequested) {
                                        navController.navigate("tile_request") {
                                            popUpTo("permissions") { inclusive = true }
                                        }
                                    } else {
                                        navController.navigate("list") {
                                            popUpTo("permissions") { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }
                        composable("tile_request") {
                            TileRequestScreen(onNext = { 
                                prefs.edit().putBoolean("tile_requested", true).apply()
                                navController.navigate("list") {
                                    popUpTo("tile_request") { inclusive = true }
                                }
                            })
                        }
                        composable("list") {
                            DumpListScreen(
                                viewModel = viewModel,
                                onDumpClick = { dumpId -> navController.navigate("detail/$dumpId") },
                                onSettingsClick = { navController.navigate("permissions") }
                            )
                        }
                        composable(
                            "detail/{dumpId}",
                            arguments = listOf(navArgument("dumpId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val dumpId = backStackEntry.arguments?.getLong("dumpId") ?: 0L
                            DumpDetailScreen(dumpId, viewModel, onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Code,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Welcome to GetUI",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "This app helps you dump the UI hierarchy of any app to an XML file. You can use it for debugging, automation, or accessibility testing.",
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Get Started")
        }
    }
}

@Composable
fun PermissionsScreen(
    isServiceEnabled: Boolean,
    areNotificationsEnabled: Boolean,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Auto navigate forward if service is enabled
    LaunchedEffect(isServiceEnabled) {
        if (isServiceEnabled) {
            onNext()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Permissions Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(24.dp))

        PermissionItem(
            title = "Notifications",
            description = "Needed to show status and success messages.",
            isGranted = areNotificationsEnabled,
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:${context.packageName}")
                    context.startActivity(intent)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionItem(
            title = "Accessibility Service",
            description = "Needed to read the UI hierarchy of other apps.",
            isGranted = isServiceEnabled,
            onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            enabled = isServiceEnabled
        ) {
            Text("Next")
        }
        if (!isServiceEnabled) {
            Text(
                text = "Please enable Accessibility Service to continue.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun PermissionItem(title: String, description: String, isGranted: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, fontWeight = FontWeight.Bold)
                Text(text = description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun TileRequestScreen(onNext: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.AddBox,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Add Quick Tile",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "For easy access, add the XML Dumper tile to your Quick Settings panel.",
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val statusBarManager = context.getSystemService(StatusBarManager::class.java)
                statusBarManager.requestAddTileService(
                    ComponentName(context, DumpTileService::class.java),
                    "Dump UI",
                    Icon.createWithResource(context, R.drawable.ic_launcher_foreground),
                    { it.run() },
                    {}
                )
            } else {
                // Inform user how to do it manually for older versions
                Toast.makeText(context, "Please add the tile manually from Quick Settings", Toast.LENGTH_LONG).show()
            }
        }) {
            Text("Add Tile")
        }
        
        TextButton(onClick = onNext) {
            Text("Skip / Continue")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DumpListScreen(
    viewModel: MainViewModel,
    onDumpClick: (Long) -> Unit,
    onSettingsClick: () -> Unit
) {
    val dumps by viewModel.allDumps.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Dumps") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        if (dumps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No dumps found. Use the Quick Tile to capture one!")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(dumps) { dump ->
                    DumpItem(dump, onClick = { onDumpClick(dump.id) }, onDelete = { viewModel.deleteDump(dump.id) })
                }
            }
        }
    }
}

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DumpItem(dump: DumpSummary, onClick: () -> Unit, onDelete: () -> Unit) {
    val date = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()).format(Date(dump.ts))
    
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { Text(dump.appName) },
        supportingContent = { Text("$date") },
        leadingContent = {
            AppIcon(packageName = dump.packageName, modifier = Modifier.size(40.dp))
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    )
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DumpDetailScreen(dumpId: Long, viewModel: MainViewModel, onBack: () -> Unit) {
    var dumpEntity by remember { mutableStateOf<DumpEntity?>(null) }
    
    LaunchedEffect(dumpId) {
        dumpEntity = viewModel.getDumpById(dumpId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (dumpEntity != null) {
                            AppIcon(packageName = dumpEntity!!.packageName, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(dumpEntity?.appName ?: "Detail")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            if (dumpEntity != null) {
                SelectionContainer {
                    Text(
                        text = dumpEntity!!.dump,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
