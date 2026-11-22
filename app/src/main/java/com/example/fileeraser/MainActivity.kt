package com.example.fileeraser

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var selectButton: Button

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            confirmAndWipe(uri)
        } else {
            statusText.text = "لم يتم اختيار أي ملف."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }

        val titleText = TextView(this).apply {
            text = "Secure File Eraser"
            textSize = 24f
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "اختر ملفًا للكتابة فوق بياناته وحذفه نهائيًا."
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 48)
        }

        selectButton = Button(this).apply {
            text = "اختيار ملف للمسح النهائي"
            setOnClickListener {
                pickFileLauncher.launch(arrayOf("*/*"))
            }
        }

        layout.addView(titleText)
        layout.addView(statusText)
        layout.addView(selectButton)
        setContentView(layout)
    }

    private fun confirmAndWipe(uri: Uri) {
        val fileName = getDisplayName(uri)
        AlertDialog.Builder(this)
            .setTitle("تأكيد المسح النهائي")
            .setMessage("سيتم الكتابة فوق محتوى الملف:\n\"$fileName\"\nوحذفه نهائيًا دون إمكانية التراجع. هل تريد المتابعة؟")
            .setPositiveButton("نعم، امسح نهائيًا") { _, _ ->
                executeWipe(uri, fileName)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun executeWipe(uri: Uri, fileName: String) {
        selectButton.isEnabled = false
        statusText.text = "جارٍ الكتابة فوق \"$fileName\" ومسحه..."

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                UriSecureEraser.wipeAndDeleteUri(this@MainActivity, uri, passes = 1)
            }
            statusText.text = result.message
            selectButton.isEnabled = true
        }
    }

    private fun getDisplayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) return cursor.getString(index)
            }
        }
        return "ملف محدد"
    }
}
