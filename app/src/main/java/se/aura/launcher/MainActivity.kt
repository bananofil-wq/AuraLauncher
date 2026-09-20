package se.aura.launcher

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AuraApp(loadApps()) { launch(it) } }
    }
    private fun loadApps(): List<ResolveInfo> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(i, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
    }
    private fun launch(info: ResolveInfo) {
        packageManager.getLaunchIntentForPackage(info.activityInfo.packageName)?.let(::startActivity)
    }
}

@Composable
fun AuraApp(apps: List<ResolveInfo>, launch: (ResolveInfo)->Unit) {
    var drawer by remember { mutableStateOf(false) }
    val bg = Color(0xFF090B0E)
    MaterialTheme(colorScheme = darkColorScheme(background=bg, surface=Color(0xFF15191E))) {
        Surface(Modifier.fillMaxSize(), color=bg) {
            if (drawer) AppDrawer(apps, launch) { drawer=false } else Home { drawer=true }
        }
    }
}

@Composable
fun Home(openDrawer:()->Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal=24.dp, vertical=36.dp)) {
        Spacer(Modifier.height(54.dp))
        Text("10:24", color=Color.White, fontSize=64.sp, fontWeight=FontWeight.Light)
        Text("söndag 20 september", color=Color.White.copy(.65f), fontSize=15.sp)
        Spacer(Modifier.weight(1f))
        GlassCard {
            Text("God morgon", color=Color.White, fontSize=25.sp)
            Text("Fokusera på det viktiga.", color=Color.White.copy(.58f))
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                Column { Text("14°", color=Color.White, fontSize=30.sp); Text("Staffanstorp", color=Color.White.copy(.6f)) }
                Column(horizontalAlignment=Alignment.End) { Text("Nästa", color=Color.White.copy(.5f)); Text("11:00  Team möte", color=Color.White) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceEvenly) {
            listOf("Telefon","Meddelanden","Kamera").forEach { label ->
                Surface(shape=RoundedCornerShape(24.dp), color=Color.White.copy(.09f),
                    modifier=Modifier.size(82.dp).clickable { if(label=="Meddelanden") openDrawer() }) {
                    Box(contentAlignment=Alignment.Center) { Text(label.take(1), color=Color.White, fontSize=22.sp) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Svep upp • appar", color=Color.White.copy(.38f), modifier=Modifier.align(Alignment.CenterHorizontally).clickable { openDrawer() })
    }
}

@Composable
fun GlassCard(content:@Composable ColumnScope.()->Unit) {
    Surface(shape=RoundedCornerShape(28.dp), color=Color.White.copy(.08f), tonalElevation=0.dp) {
        Column(Modifier.fillMaxWidth().padding(22.dp), content=content)
    }
}

@Composable
fun AppDrawer(apps:List<ResolveInfo>, launch:(ResolveInfo)->Unit, close:()->Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Appar", color=Color.White, fontSize=28.sp, modifier=Modifier.weight(1f))
            Text("Stäng", color=Color.White.copy(.6f), modifier=Modifier.clickable { close() })
        }
        Spacer(Modifier.height(18.dp))
        LazyVerticalGrid(columns=GridCells.Fixed(4), verticalArrangement=Arrangement.spacedBy(22.dp)) {
            items(apps) { app ->
                Column(horizontalAlignment=Alignment.CenterHorizontally, modifier=Modifier.clickable { launch(app) }) {
                    Surface(shape=RoundedCornerShape(20.dp), color=Color.White.copy(.08f), modifier=Modifier.size(62.dp)) {
                        Box(contentAlignment=Alignment.Center) { Text(app.loadLabel(androidx.compose.ui.platform.LocalContext.current.packageManager).toString().take(1), color=Color.White, fontSize=24.sp) }
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(app.loadLabel(androidx.compose.ui.platform.LocalContext.current.packageManager).toString(), color=Color.White.copy(.75f), fontSize=11.sp, maxLines=1)
                }
            }
        }
    }
}
