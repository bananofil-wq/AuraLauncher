package se.aura.launcher

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import java.util.Locale

data class AuraApp(
    val info: ResolveInfo,
    val label: String,
    val packageName: String,
    val activityName: String
)

class MainActivity : ComponentActivity() {

    private val apps = mutableStateOf<List<AuraApp>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.rgb(5, 6, 7)

        apps.value = loadApps()
        requestHomeRoleOnce()

        setContent {
            AuraLauncher(
                apps = apps.value,
                launchApp = ::launchApp
            )
        }
    }

    override fun onResume() {
        super.onResume()
        apps.value = loadApps()
    }

    private fun requestHomeRoleOnce() {
        val prefs = getSharedPreferences("aura_prefs", MODE_PRIVATE)
        val roleManager = getSystemService(RoleManager::class.java)

        if (
            roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
            !roleManager.isRoleHeld(RoleManager.ROLE_HOME) &&
            !prefs.getBoolean("home_role_prompted", false)
        ) {
            prefs.edit().putBoolean("home_role_prompted", true).apply()
            startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
        }
    }

    private fun loadApps(): List<AuraApp> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        return packageManager.queryIntentActivities(query, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map {
                AuraApp(
                    info = it,
                    label = it.loadLabel(packageManager).toString(),
                    packageName = it.activityInfo.packageName,
                    activityName = it.activityInfo.name
                )
            }
            .distinctBy { it.packageName to it.activityName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    private fun launchApp(app: AuraApp) {
        runCatching {
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(app.packageName, app.activityName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

            startActivity(intent)
        }.recoverCatching {
            packageManager.getLaunchIntentForPackage(app.packageName)?.let(::startActivity)
        }
    }
}
