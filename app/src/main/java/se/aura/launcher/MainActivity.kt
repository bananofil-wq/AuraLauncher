package se.aura.launcher

import android.content.Intent
import android.app.role.RoleManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.provider.Settings
import android.provider.MediaStore
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import java.text.SimpleDateFormat
import java.util.*

data class AuraApp(val info: ResolveInfo, val label: String, val pkg: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.BLACK

        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
            !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        ) {
            startActivityForResult(
                roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME),
                1001
            )
        }

        setContent { Aura(loadApps()) { launch(it) } }
    }
    private fun loadApps(): List<AuraApp> {
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(q, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { AuraApp(it, it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }
    private fun launch(app: AuraApp) {
        packageManager.getLaunchIntentForPackage(app.pkg)?.let(::startActivity)
    }
}

enum class Screen { HOME, DRAWER, FOCUS, SETTINGS }

@Composable
fun Aura(apps: List<AuraApp>, launch: (AuraApp)->Unit) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var hidden by remember { mutableStateOf(setOf<String>()) }
    var favorites by remember { mutableStateOf(setOf<String>()) }
    val visible = apps.filterNot { it.pkg in hidden }
    MaterialTheme(colorScheme = darkColorScheme(background=Color(0xFF080A0D), surface=Color(0xFF12161B))) {
        Surface(Modifier.fillMaxSize(), color=Color(0xFF080A0D)) {
            when(screen) {
                Screen.HOME -> Home(
                    favorites = visible.filter { it.pkg in favorites }.take(4),
                    launch = launch,
                    drawer = { screen=Screen.DRAWER },
                    focus = { screen=Screen.FOCUS },
                    settings = { screen=Screen.SETTINGS }
                )
                Screen.DRAWER -> Drawer(visible, favorites, launch,
                    toggleFavorite={ favorites = if(it in favorites) favorites-it else favorites+it },
                    hide={ hidden=hidden+it }, close={screen=Screen.HOME})
                Screen.FOCUS -> Focus { screen=Screen.HOME }
                Screen.SETTINGS -> SettingsScreen(hidden.size, { hidden=emptySet() }) { screen=Screen.HOME }
            }
        }
    }
}

@Composable
fun Home(favorites: List<AuraApp>, launch:(AuraApp)->Unit, drawer:()->Unit, focus:()->Unit, settings:()->Unit) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while(true) { now=Date(); kotlinx.coroutines.delay(30_000) } }
    val time=SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
    val date=SimpleDateFormat("EEEE d MMMM", Locale("sv","SE")).format(now)
    Box(Modifier.fillMaxSize().pointerInput(Unit) {
        detectVerticalDragGestures { _,dy -> if(dy < -18) drawer() else if(dy > 18) focus() }
    }) {
        Column(Modifier.fillMaxSize().padding(horizontal=24.dp)) {
            Spacer(Modifier.height(74.dp))
            Text(time, color=Color.White, fontSize=62.sp, fontWeight=FontWeight.ExtraLight, letterSpacing=(-2).sp)
            Text(date.replaceFirstChar { it.uppercase() }, color=Color.White.copy(.55f), fontSize=14.sp)
            Spacer(Modifier.height(34.dp))
            Text("God morgon", color=Color.White, fontSize=27.sp, fontWeight=FontWeight.Light)
            Text("Fokusera på det viktiga.", color=Color.White.copy(.48f), fontSize=14.sp)
            Spacer(Modifier.height(18.dp))
            AuraCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WbSunny,null,tint=Color(0xFFFFC45C),modifier=Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text("Väder",color=Color.White,fontSize=20.sp); Text("Ingen platsdata krävs",color=Color.White.copy(.45f),fontSize=12.sp) }
                    VerticalDivider(Modifier.height(46.dp),color=Color.White.copy(.1f))
                    Spacer(Modifier.width(16.dp))
                    Column { Text("Nästa",color=Color.White.copy(.4f),fontSize=12.sp); Text("Din dag",color=Color.White,fontSize=16.sp); Text("Tryck för kalender",color=Color.White.copy(.4f),fontSize=11.sp) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Quick(Icons.Outlined.CheckCircle,"Att göra",Modifier.weight(1f))
                Quick(Icons.Outlined.ShoppingBag,"Inköp",Modifier.weight(1f))
                Quick(Icons.Outlined.CenterFocusStrong,"Fokus",Modifier.weight(1f).clickable{focus()})
            }
            Spacer(Modifier.weight(1f))
            if(favorites.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceEvenly) {
                    favorites.forEach { AppIcon(it,launch) }
                }
                Spacer(Modifier.height(24.dp))
            }
            val ctx = LocalContext.current
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                CircleAction(Icons.Outlined.Phone,"Telefon") {
                    ctx.startActivity(Intent(Intent.ACTION_DIAL))
                }
                CircleAction(Icons.Outlined.Message,"Meddelanden") {
                    ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
                }
                CircleAction(Icons.Outlined.CameraAlt,"Kamera") {
                    ctx.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                }
                CircleAction(Icons.Outlined.Settings,"Inställningar",settings)
            }
            Spacer(Modifier.height(42.dp))
        }
    }
}

@Composable fun AuraCard(content:@Composable ColumnScope.()->Unit) {
    Surface(shape=RoundedCornerShape(28.dp),color=Color.White.copy(.075f),border=androidx.compose.foundation.BorderStroke(1.dp,Color.White.copy(.07f))) {
        Column(Modifier.fillMaxWidth().padding(20.dp),content=content)
    }
}
@Composable fun Quick(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,modifier:Modifier=Modifier) {
    Surface(modifier=modifier.height(62.dp),shape=RoundedCornerShape(22.dp),color=Color.White.copy(.065f)) {
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Icon(icon,null,tint=Color.White.copy(.75f),modifier=Modifier.size(20.dp)); Text(label,color=Color.White.copy(.7f),fontSize=11.sp)
        }
    }
}
@Composable fun CircleAction(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,onClick:()->Unit={}) {
    Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.clickable{onClick()}) {
        Surface(shape=CircleShape,color=Color.White.copy(.08f),modifier=Modifier.size(58.dp)) { Box(contentAlignment=Alignment.Center){Icon(icon,null,tint=Color.White.copy(.86f))} }
        Spacer(Modifier.height(7.dp)); Text(label,color=Color.White.copy(.48f),fontSize=10.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Drawer(apps:List<AuraApp>, favorites:Set<String>, launch:(AuraApp)->Unit, toggleFavorite:(String)->Unit, hide:(String)->Unit, close:()->Unit) {
    var q by remember { mutableStateOf("") }
    val shown=apps.filter { it.label.contains(q,true) }
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp)) {
        Spacer(Modifier.height(58.dp))
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Appar",color=Color.White,fontSize=30.sp,fontWeight=FontWeight.Light,modifier=Modifier.weight(1f))
            IconButton(close){Icon(Icons.Outlined.Close,null,tint=Color.White)}
        }
        OutlinedTextField(q,{q=it},Modifier.fillMaxWidth(),placeholder={Text("Sök appar")},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,shape=RoundedCornerShape(24.dp))
        Spacer(Modifier.height(22.dp))
        LazyVerticalGrid(GridCells.Fixed(4),verticalArrangement=Arrangement.spacedBy(24.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            items(shown,key={it.pkg}) { app ->
                Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.combinedClickable(onClick={launch(app)},onLongClick={toggleFavorite(app.pkg)})) {
                    RealIcon(app)
                    Spacer(Modifier.height(7.dp))
                    Text(app.label,color=if(app.pkg in favorites) Color.White else Color.White.copy(.68f),fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    if(app.pkg in favorites) Box(Modifier.padding(top=3.dp).size(4.dp).clip(CircleShape).background(Color.White.copy(.8f)))
                }
            }
        }
    }
}
@Composable fun RealIcon(app:AuraApp) {
    val pm=LocalContext.current.packageManager
    val bmp=remember(app.pkg){app.info.loadIcon(pm).toBitmap(144,144).asImageBitmap()}
    Image(bmp,app.label,Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)))
}
@Composable fun AppIcon(app:AuraApp,launch:(AuraApp)->Unit) {
    Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.clickable{launch(app)}) { RealIcon(app); Spacer(Modifier.height(5.dp)); Text(app.label,color=Color.White.copy(.65f),fontSize=10.sp,maxLines=1) }
}

@Composable fun Focus(close:()->Unit) {
    var selected by remember { mutableStateOf("Arbete") }
    val modes=listOf("Arbete" to "08:00 – 16:00","Träning" to "17:00 – 19:00","Hemmaläge" to "19:00 – 23:00","Stör ej" to "Aktivera manuellt")
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(Modifier.height(60.dp)); Row { Column(Modifier.weight(1f)){Text("Fokus",color=Color.White,fontSize=31.sp,fontWeight=FontWeight.Light);Text("Mindre brus. Mer närvaro.",color=Color.White.copy(.45f))};IconButton(close){Icon(Icons.Outlined.Close,null,tint=Color.White)}}
        Spacer(Modifier.height(34.dp))
        modes.forEach { (name,time) ->
            val active=name==selected
            Surface(Modifier.fillMaxWidth().padding(bottom=12.dp).clickable{selected=name},shape=RoundedCornerShape(25.dp),color=if(active) Color(0xFFF2EEE7) else Color.White.copy(.065f)) {
                Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(if(name=="Träning") Icons.Outlined.DirectionsRun else Icons.Outlined.Work,null,tint=if(active) Color.Black else Color.White)
                    Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f)){Text(name,color=if(active) Color.Black else Color.White,fontSize=17.sp);Text(time,color=if(active) Color.Black.copy(.55f) else Color.White.copy(.4f),fontSize=12.sp)}
                    Icon(Icons.Outlined.ChevronRight,null,tint=if(active) Color.Black else Color.White.copy(.4f))
                }
            }
        }
    }
}

@Composable fun SettingsScreen(hiddenCount:Int,resetHidden:()->Unit,close:()->Unit) {
    val ctx=LocalContext.current
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(Modifier.height(60.dp));Row(verticalAlignment=Alignment.CenterVertically){Text("Aura",color=Color.White,fontSize=31.sp,fontWeight=FontWeight.Light,modifier=Modifier.weight(1f));IconButton(close){Icon(Icons.Outlined.Close,null,tint=Color.White)}}
        Text("Minimal Launcher",color=Color.White.copy(.4f),letterSpacing=4.sp,fontSize=11.sp)
        Spacer(Modifier.height(34.dp))
        Setting("Standardlauncher","Välj Aura som hemskärm",Icons.Outlined.Home){ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))}
        Setting("Bakgrund","Öppna Androids bakgrundsväljare",Icons.Outlined.Wallpaper){ctx.startActivity(Intent(Intent.ACTION_SET_WALLPAPER))}
        Setting("Aviseringar","Hantera launcherns behörigheter",Icons.Outlined.Notifications){ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,ctx.packageName))}
        Setting("Dolda appar","$hiddenCount dolda • tryck för att återställa",Icons.Outlined.Visibility){resetHidden()}
        Setting("Integritet","Aura samlar inte in användardata",Icons.Outlined.Lock){}
        Spacer(Modifier.weight(1f));Text("AURA 1.0 • Pixel 8 Pro",color=Color.White.copy(.25f),fontSize=11.sp,modifier=Modifier.align(Alignment.CenterHorizontally));Spacer(Modifier.height(30.dp))
    }
}
@Composable fun Setting(title:String,sub:String,icon:androidx.compose.ui.graphics.vector.ImageVector,click:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable{click()}.padding(vertical=16.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Color.White.copy(.75f));Spacer(Modifier.width(18.dp));Column(Modifier.weight(1f)){Text(title,color=Color.White,fontSize=16.sp);Text(sub,color=Color.White.copy(.4f),fontSize=12.sp)};Icon(Icons.Outlined.ChevronRight,null,tint=Color.White.copy(.25f))}
    HorizontalDivider(color=Color.White.copy(.06f))
}
