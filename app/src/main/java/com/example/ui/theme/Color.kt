package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Immersive UI Palette
val ImmersiveDarkBg = Color(0xFF0D1B2A)     // #0D1B2A - Deep dark slate backdrop
val ImmersiveCardBg = Color(0xFF1B263B)     // #1B263B - Dark slate surface container
val ImmersivePrimary = Color(0xFF415A77)    // #415A77 - Middle slate blue
val ImmersiveTextSteel = Color(0xFF778DA9)  // #778DA9 - Text steel gray/blue
val ImmersiveTextLight = Color(0xFFE0E1DD)  // #E0E1DD - Bright soft white text
val ImmersiveCyan = Color(0xFF00B4D8)       // #00B4D8 - Glowing Pulse Blue / Accent Cyan

// General mapping to match existing variables perfectly so nothing breaks
val NavyDark = ImmersiveDarkBg
val NavyMedium = ImmersiveCardBg
val NavyPrimary = ImmersivePrimary
val SkyBlue = ImmersiveCyan
val SkyLight = ImmersiveTextSteel
val PureWhite = Color(0xFFFFFFFF)
val OffWhite = ImmersiveTextLight
val AlertRed = Color(0xFFEF4444)
val SuccessGreen = Color(0xFF00B4D8) // Use immersive cyan for success/connection state to keep it visually uniform and cohesive

