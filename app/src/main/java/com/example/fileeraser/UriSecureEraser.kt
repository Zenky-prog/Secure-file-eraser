package com.example.fileeraser

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.FileOutputStream
import java.security.SecureRandom
import kotlin.math.min

object UriSecureEraser {

    private const val BUFFER_SIZE = 64 * 1024 // 64 KB

    data class WipeResult(
        val success: Boolean,
        val message: String
    )

    fun wipeAndDeleteUri(context: Context, uri: Uri, passes: Int = 1): WipeResult {
        val resolver = context.contentResolver
        val secureRandom = SecureRandom()
        val buffer = ByteArray(BUFFER_SIZE)

        return try {
            val fileSize = getFileSize(context, uri)

            // 1. فتح الملف بصلاحية الكتابة المتزامنة (rwt) للكتابة فوق محتواه
            resolver.openFileDescriptor(uri, "rwt")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { outStream ->
                    if (fileSize > 0L) {
                        repeat(passes.coerceAtLeast(1)) {
                            outStream.channel.position(0L)
                            var remaining = fileSize

                            while (remaining > 0L) {
                                val chunkSize = min(buffer.size.toLong(), remaining).toInt()
                                if (chunkSize == buffer.size) {
                                    secureRandom.nextBytes(buffer)
                                    outStream.write(buffer, 0, chunkSize)
                                } else {
                                    val tail = ByteArray(chunkSize)
                                    secureRandom.nextBytes(tail)
                                    outStream.write(tail, 0, chunkSize)
                                }
                                remaining -= chunkSize
                            }
                            outStream.flush()
                            pfd.fileDescriptor.sync()
                        }
                    }
                    // تقليص الحجم إلى 0 بايت
                    outStream.channel.truncate(0L)
                    pfd.fileDescriptor.sync()
                }
            } ?: return WipeResult(false, "تعذّر فتح الملف للكتابة فوقه.")

            // 2. إعادة تسمية الملف عشوائيًا قبل الحذف (إن كان مزوّد المستندات يدعم ذلك)
            val renamedUri = try {
                val randomName = "0_${secureRandom.nextInt(1_000_000)}.tmp"
                DocumentsContract.renameDocument(resolver, uri, randomName) ?: uri
            } catch (_: Exception) {
                uri
            }

            // 3. حذف المستند نهائيًا
            val deleted = try {
                DocumentsContract.deleteDocument(resolver, renamedUri)
            } catch (_: Exception) {
                resolver.delete(renamedUri, null, null) > 0
            }

            if (deleted) {
                WipeResult(true, "تمت الكتابة فوق الملف وحذفه نهائيًا.")
            } else {
                WipeResult(false, "تم مسح محتوى الملف، لكن تعذّر حذف الإدخال من النظام.")
            }
        } catch (e: Exception) {
            WipeResult(false, "حدث خطأ: ${e.localizedMessage ?: "غير معروف"}")
        }
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                    return cursor.getLong(sizeIndex)
                }
            }
        }
        return -1L
    }
}
