package com.example.cloud

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainActivity
import com.example.player.AudioPlayerController
import com.example.ui.components.PremiumBackdrop
import com.example.ui.locale.AppLanguageManager
import com.example.ui.locale.LanguageMode
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeManager

class WebAccessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.init(this)
        AppLanguageManager.init(this)
        CloudSyncManager.init(this)
        enableEdgeToEdge()

        setContent {
            val appTheme by ThemeManager.currentTheme.collectAsState()
            val isArabic by AppLanguageManager.isArabicFlow.collectAsState()
            val layoutDirection = AppLanguageManager.currentLayoutDirection(isArabic)

            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                MyApplicationTheme(appTheme = appTheme) {
                    PremiumBackdrop {
                        WebAccessScreen(
                            onBack = {
                                if (isTaskRoot) {
                                    startActivity(Intent(this@WebAccessActivity, MainActivity::class.java))
                                }
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebAccessScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentUser by CloudSyncManager.currentUser.collectAsState()
    val playbackState by CloudSyncManager.playbackState.collectAsState()
    val syncStatus by CloudSyncManager.syncStatus.collectAsState()
    val languageMode by AppLanguageManager.languageMode.collectAsState()
    val isArabic by AppLanguageManager.isArabicFlow.collectAsState()

    var isSignUpMode by remember { mutableStateOf(false) }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var displayNameInput by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isArabic) "بوابة الويب والمزامنة السحابية" else "DJ Web & Cloud Sync",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "dj-web-topaz.vercel.app • $syncStatus",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("web_access_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val next = when (languageMode) {
                                LanguageMode.AUTO -> LanguageMode.ARABIC
                                LanguageMode.ARABIC -> LanguageMode.ENGLISH
                                LanguageMode.ENGLISH -> LanguageMode.AUTO
                            }
                            AppLanguageManager.setLanguageMode(context, next)
                            CloudSyncManager.saveUserIsolatedData(
                                context = context,
                                languageMode = next.code
                            )
                        },
                        modifier = Modifier.testTag("web_access_language_button")
                    ) {
                        Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (languageMode) {
                                LanguageMode.AUTO -> if (isArabic) "تلقائي" else "Auto"
                                LanguageMode.ARABIC -> "العربية"
                                LanguageMode.ENGLISH -> "EN"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (currentUser != null) {
                        IconButton(
                            onClick = { CloudSyncManager.signOut(context) },
                            modifier = Modifier.testTag("web_access_sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = if (isArabic) "تسجيل الخروج" else "Sign Out"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Browser Web Access & Share Link Card (Browser only — no embedded WebView button)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column {
                            Text(
                                text = if (isArabic) "فتح تطبيق الويب في المتصفح" else "Open Web App in Browser",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "https://dj-web-topaz.vercel.app/",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isArabic) {
                                    "افتح نسخة الويب في المتصفح الخارجي مع نقل جلسة حسابك تلقائياً."
                                } else {
                                    "Launch DJ Web in your external browser with automatic session handoff."
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val url = CloudSyncManager.buildAuthenticatedWebUrl(context)
                            runCatching {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(browserIntent)
                            }.onFailure {
                                Toast.makeText(context, url, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("web_access_open_browser_button")
                    ) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isArabic) "فتح صفحة الويب في المتصفح (Open Web)" else "Open Web in Browser",
                            maxLines = 1,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val shareUrl = CloudSyncManager.WEB_APP_SHARED_URL + "/"
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_SUBJECT,
                                    if (isArabic) "تطبيق DJ Web Studio" else "DJ Web Studio"
                                )
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    if (isArabic) "تطبيق DJ Web Studio السحابي: $shareUrl" else "Join DJ Web Studio Cloud: $shareUrl"
                                )
                            }
                            val chooser = Intent.createChooser(
                                sendIntent,
                                if (isArabic) "مشاركة رابط الويب" else "Share Web Link"
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { context.startActivity(chooser) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("web_access_share_link_button")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isArabic) "مشاركة رابط الويب (Share Link)" else "Share Link")
                    }
                }
            }

            // Authentication Card (secure email/password cloud authentication)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val user = currentUser
                    if (user != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = user.displayName.take(1).uppercase(),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                                Column {
                                    Text(
                                        text = user.displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        modifier = Modifier.testTag("auth_active_user_name")
                                    )
                                    Text(
                                        text = "${user.email} • ${user.authProvider.uppercase()}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.testTag("auth_active_user_email")
                                    )
                                    Text(
                                        text = "Isolated Vault ID: ${user.userId}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { CloudSyncManager.signOut(context) },
                                modifier = Modifier.testTag("auth_card_sign_out_button")
                            ) {
                                Text(if (isArabic) "خروج" else "Sign Out")
                            }
                        }
                    } else {
                        Text(
                            text = if (isArabic) {
                                if (isSignUpMode) "إنشاء حساب سحابي جديد (Neon Auth)" else "تسجيل الدخول للمزامنة السحابية"
                            } else {
                                if (isSignUpMode) "Create Neon Cloud Account" else "Sign In to Neon Cloud Sync"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        Text(
                            text = if (isArabic) {
                                "استخدم البريد الإلكتروني وكلمة المرور لمزامنة مكتبتك بين الهاتف والويب."
                            } else {
                                "Use your email and password to securely sync your library between Android and web."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (authError != null) {
                            Text(
                                text = authError!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                modifier = Modifier.testTag("auth_error_text")
                            )
                        }

                        if (isSignUpMode) {
                            OutlinedTextField(
                                value = displayNameInput,
                                onValueChange = { displayNameInput = it },
                                label = { Text(if (isArabic) "الاسم" else "Display Name") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_name_input")
                            )
                        }

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it; authError = null },
                            label = { Text(if (isArabic) "البريد الإلكتروني" else "Email Address") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_email_input")
                        )

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it; authError = null },
                            label = { Text(if (isArabic) "كلمة المرور" else "Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_password_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val result = if (isSignUpMode) {
                                        CloudSyncManager.signUpWithEmail(
                                            context = context,
                                            email = emailInput,
                                            password = passwordInput,
                                            displayName = displayNameInput
                                        )
                                    } else {
                                        CloudSyncManager.signInWithEmail(
                                            context = context,
                                            email = emailInput,
                                            password = passwordInput
                                        )
                                    }
                                    result.onSuccess {
                                        authError = null
                                        passwordInput = ""
                                    }.onFailure { err ->
                                        authError = err.message ?: "Authentication failed"
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("auth_submit_email_button")
                            ) {
                                Text(
                                    if (isSignUpMode) {
                                        if (isArabic) "تسجيل حساب" else "Sign Up"
                                    } else {
                                        if (isArabic) "تسجيل الدخول" else "Sign In"
                                    }
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    isSignUpMode = !isSignUpMode
                                    authError = null
                                },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("auth_toggle_mode_button")
                            ) {
                                Text(
                                    if (isSignUpMode) {
                                        if (isArabic) "لدي حساب" else "Have Account"
                                    } else {
                                        if (isArabic) "حساب جديد" else "Create Account"
                                    }
                                )
                            }
                        }

                    }
                }
            }

            // Single Active Audio Output & Device Handoff Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val myDeviceId = remember { CloudSyncManager.getDeviceId(context) }
                    val activePlatform = playbackState?.activeDevicePlatform?.uppercase() ?: "ANDROID"
                    val isThisDeviceActive = playbackState == null || playbackState?.activeDeviceId == myDeviceId

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isArabic) "التحكم في مخرج الصوت النشط (جهاز واحد فقط)" else "Single Active Audio Output Arbitration",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = if (isThisDeviceActive) {
                                    if (isArabic) "الصوت نشط حالياً على هذا الهاتف (Android)" else "Audio output is currently assigned to this Android phone"
                                } else {
                                    if (isArabic) "الصوت نشط حالياً على منصة: $activePlatform" else "Audio output is currently active on: $activePlatform"
                                },
                                fontSize = 12.sp,
                                color = if (isThisDeviceActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.testTag("handoff_active_device_status")
                            )
                        }
                        Icon(
                            imageVector = if (isThisDeviceActive) Icons.Filled.PhoneAndroid else Icons.Filled.Computer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (playbackState?.currentTrackTitle?.isNotBlank() == true) {
                        Text(
                            text = "♫ ${playbackState?.currentTrackTitle} — ${playbackState?.currentTrackArtist}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = {
                            val controller = AudioPlayerController.obtain(context)
                            CloudSyncManager.claimAudioOutput(
                                context = context,
                                isPlaying = true,
                                currentSong = controller.currentSong,
                                positionMs = controller.currentPositionMs
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("handoff_claim_android_output_button")
                    ) {
                        Icon(Icons.Filled.SpeakerPhone, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isArabic) {
                                "نقل الصوت إلى هذا الهاتف وإيقاف الويب"
                            } else {
                                "Activate Audio Here & Pause Web Output"
                            }
                        )
                    }
                }
            }
        }
    }
}
