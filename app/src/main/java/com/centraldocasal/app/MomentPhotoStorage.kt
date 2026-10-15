package com.centraldocasal.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.centraldocasal.app.data.SessionState
import com.centraldocasal.app.domain.Record
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

private const val PHOTO_MAX_BYTES = 4L * 1024 * 1024
private val storageId = Regex("[A-Za-z0-9_-]{1,150}")

internal fun momentPhotoPath(session: SessionState, record: Record, suffix: String): String {
    require(record.kind == "moment") { "Escolha uma lembrança válida." }
    require(!session.local && session.uid.matches(storageId)) { "Entre na sua conta para adicionar fotos." }
    require(record.id.matches(storageId) && suffix.matches(storageId)) { "Identificação da foto inválida." }
    val prefix = if (record.isPrivate) "users/${session.uid}" else {
        require(session.household.matches(storageId)) { "Conecte o espaço do casal para compartilhar esta foto." }
        "households/${session.household}"
    }
    return "$prefix/moments/${record.id}/${session.uid}-$suffix.jpg"
}

internal object MomentPhotoStorage {
    private fun scaledImage(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= 28) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                require(width > 0 && height > 0 && width.toLong() * height <= 120_000_000L) { "Imagem muito grande." }
                val ratio = minOf(1.0, 1600.0 / maxOf(width, height).toDouble())
                decoder.setTargetSize((width * ratio).toInt().coerceAtLeast(1), (height * ratio).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        require(options.outWidth > 0 && options.outHeight > 0 &&
            options.outWidth.toLong() * options.outHeight <= 120_000_000L) { "Imagem inválida ou grande demais." }
        options.inJustDecodeBounds = false
        options.inSampleSize = (maxOf(options.outWidth, options.outHeight) / 1600).coerceAtLeast(1)
        return requireNotNull(resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }) {
            "Não foi possível abrir esta imagem."
        }
    }

    suspend fun upload(context: Context, session: SessionState, record: Record, uri: Uri): String {
        val bytes = withContext(Dispatchers.IO) {
            require(context.contentResolver.getType(uri)?.startsWith("image/") == true) { "Escolha uma imagem." }
            val bitmap = scaledImage(context, uri)
            val stream = ByteArrayOutputStream()
            try {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, stream)
                if(stream.size().toLong() > PHOTO_MAX_BYTES) {
                    stream.reset()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 62, stream)
                }
            } finally { bitmap.recycle() }
            require(stream.size() in 1..PHOTO_MAX_BYTES.toInt()) { "Reduza o tamanho da foto e tente novamente." }
            stream.toByteArray()
        }
        val path = momentPhotoPath(session, record, UUID.randomUUID().toString())
        val ref = FirebaseStorage.getInstance().reference.child(path)
        val meta = StorageMetadata.Builder().setContentType("image/jpeg").build()
        ref.putBytes(bytes, meta).await()
        return path
    }

    suspend fun removeUnlinked(path: String) {
        runCatching { FirebaseStorage.getInstance().reference.child(path).delete().await() }
    }

    suspend fun preview(path: String): ImageBitmap = withContext(Dispatchers.IO) {
        val bytes = FirebaseStorage.getInstance().reference.child(path).getBytes(PHOTO_MAX_BYTES).await()
        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
        requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)).asImageBitmap()
    }
}

@Composable
internal fun MomentPhotoPreview(record: Record, enabled: Boolean, choose: () -> Unit) {
    val path = record.text("photoPath")
    var preview by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember(path) { mutableStateOf(path.isNotEmpty()) }
    var error by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path) {
        if (path.isNotBlank()) {
            try { preview = MomentPhotoStorage.preview(path) }
            catch (_: Exception) { error = true }
            finally { loading = false }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            preview != null -> Image(
                bitmap = requireNotNull(preview),
                contentDescription = "Foto da lembrança ${record.text("title")}",
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentScale = ContentScale.Crop
            )
            loading -> LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
            error -> Text("Não foi possível carregar a foto neste aparelho.",
                color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = choose, enabled = enabled) {
            Text(if(path.isEmpty()) "Adicionar foto" else "Trocar foto")
        }
        if (!enabled) Text("Fotos precisam de conta conectada. Só os participantes autorizados podem vê-las.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
