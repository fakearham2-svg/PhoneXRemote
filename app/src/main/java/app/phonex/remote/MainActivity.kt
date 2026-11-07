package app.phonex.remote

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

private val Bg = Color(0xFF0A0F1E)
private val Panel = Color(0xFF151D31)
private val Purple = Color(0xFF805DFF)
private val Mint = Color(0xFF37D3B1)
private const val DATABASE_URL = "https://phonexremote-448f3-default-rtdb.asia-southeast1.firebasedatabase.app"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PhoneXApp() }
    }
}

@Composable
fun PhoneXApp() {
    val context = LocalContext.current
    val database = remember { FirebaseDatabase.getInstance(DATABASE_URL).reference }
    var role by remember { mutableStateOf("Controller") }
    var torchOn by remember { mutableStateOf(false) }
    val battery = remember { readBattery(context) }
    var authStatus by remember { mutableStateOf("Connecting to Firebase…") }
    var userId by remember { mutableStateOf<String?>(null) }
    var receiverId by remember { mutableStateOf("") }
    var pairingStatus by remember { mutableStateOf("Not paired") }
    var incomingControllers by remember { mutableStateOf(setOf<String>()) }
    var requestSent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val auth = FirebaseAuth.getInstance()
        val existing = auth.currentUser
        if (existing != null) {
            userId = existing.uid
            authStatus = "Firebase connected"
        } else {
            auth.signInAnonymously()
                .addOnSuccessListener { result ->
                    userId = result.user?.uid
                    authStatus = if (userId != null) "Firebase connected" else "Authentication incomplete"
                }
                .addOnFailureListener { error ->
                    authStatus = "Firebase error: ${error.localizedMessage ?: "Please check setup"}"
                }
        }
    }

    DisposableEffect(userId, role) {
        val uid = userId
        if (uid == null || role != "Receiver") {
            onDispose { }
        } else {
            val requestsRef = database.child("pairingRequests").child(uid)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    incomingControllers = snapshot.children.mapNotNull { child ->
                        child.child("controllerUid").getValue(String::class.java)
                    }.toSet()
                }
                override fun onCancelled(error: DatabaseError) {
                    pairingStatus = "Pairing read blocked: check database rules"
                }
            }
            requestsRef.addValueEventListener(listener)
            onDispose { requestsRef.removeEventListener(listener) }
        }
    }

    DisposableEffect(userId, receiverId, role, requestSent) {
        val uid = userId
        val target = receiverId.trim()
        if (uid == null || role != "Controller" || !requestSent || target.isBlank()) {
            onDispose { }
        } else {
            val approvalRef = database.child("pairingApprovals").child(uid).child(target)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.child("status").getValue(String::class.java) == "approved") {
                        pairingStatus = "Paired — receiver approved"
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    pairingStatus = "Approval check blocked: check database rules"
                }
            }
            approvalRef.addValueEventListener(listener)
            onDispose { approvalRef.removeEventListener(listener) }
        }
    }

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
                        Text("v1.2", color = Color.White, modifier = Modifier.padding(12.dp))
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("FIREBASE STATUS", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(authStatus, color = Color.White, fontSize = 14.sp)
                        if (userId != null) {
                            Text("Your Device ID", color = Color.LightGray, fontSize = 11.sp)
                            Text(userId!!, color = Color.White, fontSize = 11.sp)
                            OutlinedButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("PhoneX Device ID", userId!!))
                                Toast.makeText(context, "Device ID copied", Toast.LENGTH_SHORT).show()
                            }) { Text("Copy my Device ID") }
                        }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("DEVICE ROLE", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            listOf("Controller", "Receiver").forEachIndexed { index, item ->
                                SegmentedButton(selected = role == item, onClick = {
                                    role = item
                                    requestSent = false
                                    pairingStatus = "Not paired"
                                }, shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(item) }
                            }
                        }
                        Text(if (role == "Controller") "Enter the other phone's Device ID to request pairing." else "Share this phone's Device ID with the controller. Approve only people you know.", color = Color.LightGray, fontSize = 13.sp)
                    }
                }
                if (role == "Controller") {
                    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("PAIR WITH RECEIVER", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = receiverId,
                                onValueChange = { receiverId = it.trim(); requestSent = false; pairingStatus = "Not paired" },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Receiver Device ID") },
                                singleLine = true
                            )
                            Button(
                                enabled = userId != null && receiverId.trim().length >= 20 && receiverId.trim() != userId,
                                onClick = {
                                    val target = receiverId.trim()
                                    val uid = userId ?: return@Button
                                    val request = mapOf("controllerUid" to uid, "createdAt" to System.currentTimeMillis(), "status" to "pending")
                                    database.child("pairingRequests").child(target).child(uid).setValue(request)
                                        .addOnSuccessListener {
                                            requestSent = true
                                            pairingStatus = "Request sent — waiting for receiver approval"
                                        }
                                        .addOnFailureListener { error ->
                                            pairingStatus = "Request failed: ${error.localizedMessage ?: "check database rules"}"
                                        }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Purple)
                            ) { Text("Send pairing request") }
                            Text(pairingStatus, color = if (pairingStatus.startsWith("Paired")) Mint else Color.LightGray, fontSize = 12.sp)
                        }
                    }
                } else {
                    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("PAIRING REQUESTS", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            if (incomingControllers.isEmpty()) {
                                Text("No incoming requests yet.", color = Color.LightGray, fontSize = 13.sp)
                            } else {
                                incomingControllers.forEach { controllerUid ->
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Controller: ${controllerUid.take(12)}…", color = Color.White, fontSize = 13.sp)
                                        Button(onClick = {
                                            val uid = userId ?: return@Button
                                            val approval = mapOf("controllerUid" to controllerUid, "receiverUid" to uid, "status" to "approved", "approvedAt" to System.currentTimeMillis())
                                            database.child("pairingApprovals").child(controllerUid).child(uid).setValue(approval)
                                                .addOnSuccessListener {
                                                    pairingStatus = "Approved pairing with ${controllerUid.take(8)}…"
                                                    database.child("pairingRequests").child(uid).child(controllerUid).removeValue()
                                                }
                                                .addOnFailureListener { error ->
                                                    pairingStatus = "Approval failed: ${error.localizedMessage ?: "check database rules"}"
                                                }
                                        }, colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("Approve this phone") }
                                    }
                                    Divider(color = Color.DarkGray)
                                }
                            }
                            Text(pairingStatus, color = Mint, fontSize = 12.sp)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoCard("Battery", "$battery%", Modifier.weight(1f))
                    InfoCard("Pairing", if (pairingStatus.startsWith("Paired") || pairingStatus.startsWith("Approved")) "Approved" else "Pending", Modifier.weight(1f))
                }
                Text(if (role == "Controller") "LOCAL CONTROLS" else "RECEIVER TEST CONTROLS", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                ControlCard("Lock screen", "Not enabled in this starter build",
                    { Icon(Icons.Default.Lock, null, tint = Mint) }, "Not enabled", enabled = false, onClick = {})
                ControlCard("Open app", "Android may restrict background app launches",
                    { Icon(Icons.Default.Settings, null, tint = Mint) }, "Coming next", enabled = false, onClick = {})
                Text("Pairing requests require the receiver's approval. Pairing is not a remote-control feature; remote commands are still disabled. Configure the matching secure database rules before testing.", color = Color.Gray, fontSize = 11.sp)
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
