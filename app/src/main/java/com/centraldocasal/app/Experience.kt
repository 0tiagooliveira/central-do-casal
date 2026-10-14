package com.centraldocasal.app

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ExperienceOptions(val haptics: Boolean = true, val motion: Boolean = true, val photo: String = "", val sounds: Boolean = false)
internal val LocalExperience = staticCompositionLocalOf { ExperienceOptions() }
private fun readExperience(prefs: SharedPreferences) = ExperienceOptions(prefs.getBoolean("haptics", true), prefs.getBoolean("motion", true), prefs.getString("photo", "").orEmpty(),prefs.getBoolean("sounds",false))

@Composable
internal fun rememberExperience(uid: String): ExperienceOptions {
    val context=LocalContext.current
    val prefs=remember(uid) { context.getSharedPreferences("experience_$uid", Context.MODE_PRIVATE) }
    var options by remember(uid) { mutableStateOf(readExperience(prefs)) }
    DisposableEffect(prefs) {
        val listener=SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> options=readExperience(prefs) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return options
}

@Composable
internal fun PersonalPortrait(uri: String, name: String, modifier: Modifier = Modifier, size: Dp = 58.dp) {
    val context=LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap=if(uri.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                val parsed=Uri.parse(uri)
                val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                context.contentResolver.openInputStream(parsed)?.use { android.graphics.BitmapFactory.decodeStream(it,null,bounds) }
                val sample=generateSequence(1) { it*2 }.first { bounds.outWidth/it<=512 && bounds.outHeight/it<=512 }
                val opts=android.graphics.BitmapFactory.Options().apply { inSampleSize=sample }
                context.contentResolver.openInputStream(parsed)?.use { android.graphics.BitmapFactory.decodeStream(it,null,opts)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    Surface(modifier.size(size),shape=CircleShape,color=Color.White) {
        if(bitmap!=null) Image(bitmap!!,"Foto escolhida por $name",Modifier.clip(CircleShape),contentScale=ContentScale.Crop)
        else Icon(Icons.Default.Person,"Perfil de $name",Modifier.size(28.dp),tint=MaterialTheme.colorScheme.primary)
    }
}
