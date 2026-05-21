package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraEnhance
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material.icons.filled.SettingsCell
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.AppScreen
import com.example.ui.theme.*

@Composable
fun MainMenuScreen(
    onNavigate: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    // Beautiful flowing background using gradient
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(NavyDark, Color(0xFF1E1B4B))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Decorative abstract glowing background blobs
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawCircle(
                        color = SkyBlue.copy(alpha = 0.15f),
                        radius = 280.dp.toPx(),
                        center = Offset(size.width * 0.9f, size.height * 0.15f)
                    )
                    drawCircle(
                        color = NavyPrimary.copy(alpha = 0.28f),
                        radius = 350.dp.toPx(),
                        center = Offset(size.width * 0.1f, size.height * 0.75f)
                    )
                }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Hero Section (Aesthetic Typography)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp)
            ) {
                // Top branded micro-badge
                Row(
                    modifier = Modifier
                        .background(SkyBlue.copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, SkyBlue.copy(alpha = 0.3f), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(SkyBlue, CircleShape)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "FOTO ESTUDIO BLUETOOTH",
                        color = SkyBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "ClickRemoto",
                    color = PureWhite,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Controla el disparador de tu cámara de forma inalámbrica usando un segundo teléfono cercano.",
                    color = OffWhite.copy(alpha = 0.7f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            // Central Interactive Mockup Card
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .background(NavyMedium, RoundedCornerShape(32.dp))
                    .border(1.dp, PureWhite.copy(alpha = 0.08f), RoundedCornerShape(32.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Subtle blur halo effect directly inside card
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(SkyBlue.copy(alpha = 0.08f), CircleShape)
                        .padding(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraEnhance,
                        contentDescription = "Icono Ilustrativo",
                        tint = SkyBlue,
                        modifier = Modifier.size(80.dp).align(Alignment.Center)
                    )
                }
            }

            // Dual Core Selection Mode Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Button 1: Host Camera Mode
                ElevatedButton(
                    onClick = { onNavigate(AppScreen.CAMERA) },
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = NavyMedium, // #1B263B Dark Slate Card Base
                        contentColor = OffWhite      // #E0E1DD Soft White Text
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .border(1.dp, PureWhite.copy(alpha = 0.05f), RoundedCornerShape(18.dp))
                        .testTag("use_as_camera_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SettingsCell,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = SkyBlue // Pulse Accent Cyan
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Column {
                            Text(
                                text = "Usar como Cámara",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Colocar en tripié para recibir disparos",
                                fontSize = 11.sp,
                                color = SkyLight // #778DA9 Steel Grey
                            )
                        }
                    }
                }

                // Button 2: Remote Shutter Mode
                ElevatedButton(
                    onClick = { onNavigate(AppScreen.REMOTE) },
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = NavyPrimary, // #415A77 Mid Slate Blue
                        contentColor = PureWhite
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .border(1.dp, PureWhite.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                        .testTag("use_as_remote_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhonelinkRing,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = PureWhite
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Column {
                            Text(
                                text = "Usar como Control Remoto",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Disparar ráfagas y temporizador lejanos",
                                fontSize = 11.sp,
                                color = OffWhite.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}
