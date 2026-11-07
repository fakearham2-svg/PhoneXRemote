package app.phonex.remote

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val Bg = Color(0xFF0A0F1E)
private val Panel = Color(0xFF151D31)
private val Purple = Color(0xFF805DFF)
private val Mint = Color(0xFF37D3B1)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PhoneXApp() }
    }
}

@Composable
fun PhoneXApp() {
    val context = LocalContext.current
    var role by remember { mutableStateOf("Controller") }
    var torchOn by remember { mutableStateOf(false) }
    var battery by remember { mutableIntStateOf(readBattery(context)) }
    var paired by remember { mutableStateOf(false) }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(context, "Camera permission is needed for flashlight control.", Toast.LENGTH_LONG).show()
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Purple, secondary = Mint, background = Bg, surface = Panel)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("PhoneX Remote", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text("Remote device dashboard", color = Color.LightGray, fontSize = 13.sp)
                    }
                    Surface(color = Purple.copy(alpha = .18f), shape = RoundedCornerShape(14.dp)) {
                        Text("v1.0", color = Color.White, modifier = Modifier.padding(12.dp))
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("DEVICE ROLE", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            listOf("Controller", "Receiver").forEachIndexed { index, item ->
                                SegmentedButton(selected = role == item, onClick = { role = item }, shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(item) }
                            }
                        }
                        Text(if (role == "Controller") "Use this phone to send approved commands." else "This phone will receive commands after pairing and consent.", color = Color.LightGray, fontSize = 13.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoCard("Battery", "$battery%", Modifier.weight(1f))
                    InfoCard("Connection", if (paired) "Paired" else "Not paired", Modifier.weight(1f))
                }
                Text(if (role == "Controller") "REMOTE CONTROLS" else "RECEIVER TEST CONTROLS", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                ControlCard("Flashlight", if (torchOn) "Currently ON" else "Currently OFF",
                    { Icon(Icons.Default.FlashlightOn, null, tint = Mint) }, if (torchOn) "Turn off" else "Turn on",
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        } else { torchOn = setTorch(context, !torchOn) }
                    })
                ControlCard("Wi-Fi settings", "Android controls Wi-Fi changes through system UI",
                    { Icon(Icons.Default.Wifi, null, tint = Mint) }, "Open settings",
                    onClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) })
                ControlCard("Bluetooth settings", "Choose Bluetooth state in Android settings",
                    { Icon(Icons.Default.Bluetooth, null, tint = Mint) }, "Open settings",
                    onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) })
                ControlCard("Lock screen", "Requires explicit Device Admin setup (not enabled in this starter)",
                    { Icon(Icons.Default.Lock, null, tint = Mint) }, "Not enabled", enabled = false, onClick = {})
                ControlCard("Open app", "Android may restrict launching other apps from background",
                    { Icon(Icons.Default.Settings, null, tint = Mint) }, "App picker coming next", enabled = false, onClick = {})
                Button(onClick = {
                    paired = false
                    Toast.makeText(context, "Cloud pairing will be enabled after Firebase setup.", Toast.LENGTH_LONG).show()
                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Purple)) {
                    Text("Set up secure pairing")
                }
                Text("Starter build: controls on this screen operate this phone only. Remote commands are intentionally disabled until Firebase authentication, pairing and server-side authorization are configured.", color = Color.Gray, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = Color.LightGray, fontSize = 12.sp)
            Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ControlCard(title: String, subtitle: String, icon: @Composable () -> Unit, action: String, enabled: Boolean = true, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) { icon() }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Color.LightGray, fontSize = 11.sp)
            }
            OutlinedButton(onClick = onClick, enabled = enabled) { Text(action, fontSize = 11.sp) }
        }
    }
}

private fun readBattery(context: Context): Int {
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
}

private fun setTorch(context: Context, enabled: Boolean): Boolean {
    return try {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return false
        manager.setTorchMode(cameraId, enabled)
        enabled
    } catch (e: Exception) {
        Toast.makeText(context, "Could not change flashlight: ${e.message}", Toast.LENGTH_LONG).show()
        false
    }
}
