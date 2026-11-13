package com.example.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.PdfGoldPrimary
import java.io.File
import java.io.FileOutputStream

object LogoHelper {

    fun saveLogoFromUri(context: Context, uri: Uri, oldLogoPath: String = ""): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val decoded = inputStream.use { BitmapFactory.decodeStream(it) } ?: return null

            val maxDim = 512
            val width = decoded.width
            val height = decoded.height
            val scale = minOf(1f, maxDim.toFloat() / maxOf(width, height).coerceAtLeast(1))
            val targetW = (width * scale).toInt().coerceAtLeast(1)
            val targetH = (height * scale).toInt().coerceAtLeast(1)

            val scaledBitmap = if (targetW != width || targetH != height) {
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            } else {
                decoded
            }

            val logoDir = File(context.filesDir, "store_logos")
            if (!logoDir.exists()) logoDir.mkdirs()

            if (oldLogoPath.isNotBlank()) {
                deleteLogoFile(context, oldLogoPath)
            }

            val destFile = File(logoDir, "store_logo_${System.currentTimeMillis()}.png")
            FileOutputStream(destFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            if (scaledBitmap !== decoded) {
                scaledBitmap.recycle()
            }
            decoded.recycle()
            destFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun deleteLogoFile(context: Context, logoPath: String) {
        if (logoPath.isBlank()) return
        try {
            val file = File(logoPath)
            val filesDirPath = context.filesDir.canonicalPath
            if (file.exists() && file.canonicalPath.startsWith(filesDirPath)) {
                file.delete()
            }
        } catch (_: Exception) {
        }
    }

    fun loadLogoBitmap(logoPath: String): Bitmap? {
        if (logoPath.isBlank()) return null
        return try {
            val file = File(logoPath)
            if (!file.exists()) null else BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun StoreLogoBadge(
    logoPath: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    cornerRadius: Dp = 10.dp,
    containerColor: Color = Color.White.copy(alpha = 0.14f),
    borderColor: Color? = null,
    contentDescription: String = "شعار المحل"
) {
    val bitmap = remember(logoPath) {
        LogoHelper.loadLogoBitmap(logoPath)?.asImageBitmap()
    }
    val shape = RoundedCornerShape(cornerRadius)
    val baseModifier = modifier
        .size(size)
        .clip(shape)
        .background(if (bitmap != null) Color.White else containerColor)
        .let { m ->
            if (borderColor != null) m.border(1.dp, borderColor, shape) else m
        }

    Box(
        modifier = baseModifier,
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.Storefront,
                contentDescription = contentDescription,
                tint = PdfGoldPrimary,
                modifier = Modifier.size(size * 0.58f)
            )
        }
    }
}
