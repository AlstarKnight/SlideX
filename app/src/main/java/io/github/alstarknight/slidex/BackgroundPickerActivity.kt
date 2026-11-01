package io.github.alstarknight.slidex

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Invisible activity the panel starts to pick a page's background picture, since the system photo picker cannot
 * show above the panel. The picture is saved to the page, then the panel opens on that page again.
 */
class BackgroundPickerActivity : ComponentActivity() {
    private var pageIndex = 0

    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) {
            reopenPanel()
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            val context = this@BackgroundPickerActivity
            val picture = withContext(Dispatchers.IO) { BackgroundImages.import(context, uri) }
            if (picture == null) {
                Toast.makeText(context, R.string.background_failed, Toast.LENGTH_SHORT).show()
            } else {
                val store = ConfigStore.get(context)
                store.update { it.setPageStyle(pageIndex) { style -> style.copy(background = picture) } }
                store.deleteUnused()
            }
            reopenPanel()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageIndex = intent.getIntExtra(EXTRA_PAGE, 0)
        // After a recreation the picker is already open and its result comes back to the launcher above.
        if (savedInstanceState == null) {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    private fun reopenPanel() {
        PanelService.openPanel(this, pageIndex)
        finish()
    }

    companion object {
        private const val EXTRA_PAGE = "page"

        fun intent(context: Context, pageIndex: Int): Intent =
            Intent(context, BackgroundPickerActivity::class.java).putExtra(EXTRA_PAGE, pageIndex)
    }
}
