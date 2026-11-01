package io.github.alstarknight.slidex

import android.content.Context
import android.util.Log
import androidx.core.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/** A kept config: its file is named after [id], and the settings list it by [name]. */
@Serializable
data class ConfigProfile(val id: String, val name: String)

/**
 * App-wide holder of the panel config in use; changes are written to its file in the background. Several configs
 * can be kept and switched between: the first lives in `files/config.json`, the others in `files/configs`.
 */
@OptIn(FlowPreview::class)
class ConfigStore private constructor(private val context: Context) {
    private val prefs = Prefs(context)

    // Held while the config is saved or switched, so a save cannot land in the file of the config switched to.
    private val lock = Any()
    private val _active = MutableStateFlow(prefs.activeConfig)
    private val _config = MutableStateFlow(load(_active.value))
    private val _profiles = MutableStateFlow(prefs.configProfiles)

    val config: StateFlow<PanelConfig> = _config

    /** Id of the config in use. */
    val active: StateFlow<String> = _active

    val profiles: StateFlow<List<ConfigProfile>> = _profiles

    init {
        // Typing in a note or dragging a slider changes the config many times a second; it is written once they pause.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            _config.drop(1).debounce(SAVE_DELAY_MS).collect { save(it) }
        }
    }

    fun update(transform: (PanelConfig) -> PanelConfig) {
        _config.update(transform)
    }

    /** Switches to the config [id], saving the one in use first. */
    fun switchTo(id: String) {
        synchronized(lock) {
            if (id == _active.value) return
            write(file(_active.value), _config.value)
            prefs.activeConfig = id
            _active.value = id
            _config.value = load(id)
        }
    }

    /** Keeps [config] as a new config called [name] and switches to it. */
    fun add(name: String, config: PanelConfig = defaultConfig(context)) {
        val id = newId()
        write(file(id), config)
        setProfiles(_profiles.value + ConfigProfile(id, name))
        switchTo(id)
    }

    fun rename(id: String, name: String) {
        setProfiles(_profiles.value.map { if (it.id == id) it.copy(name = name) else it })
    }

    /** Deletes the config [id], and the widgets and pictures only it used; the config in use cannot be deleted. */
    fun delete(id: String) {
        if (id == _active.value) return
        file(id).delete()
        setProfiles(_profiles.value.filterNot { it.id == id })
        deleteUnused()
    }

    /** Frees the widgets and deletes the background pictures that no kept config uses any more. */
    fun deleteUnused() {
        val configs = _profiles.value.map { if (it.id == _active.value) _config.value else load(it.id) }
        WidgetHost.deleteUnused(context, configs)
        BackgroundImages.deleteUnused(context, configs)
    }

    private fun setProfiles(profiles: List<ConfigProfile>) {
        _profiles.value = profiles
        prefs.configProfiles = profiles
    }

    private fun file(id: String): AtomicFile = AtomicFile(
        if (id == Prefs.DEFAULT_CONFIG) File(context.filesDir, "config.json") else File(context.filesDir, "configs/$id.json"),
    )

    private fun load(id: String): PanelConfig {
        val file = file(id)
        if (!file.baseFile.exists()) return defaultConfig(context)
        return try {
            parseConfig(file.readFully().decodeToString())
        } catch (e: IllegalArgumentException) {
            // Keep the unreadable file for inspection instead of overwriting it.
            Log.w(TAG, "Config is invalid, starting from defaults", e)
            file.baseFile.renameTo(File(file.baseFile.path + ".bad"))
            defaultConfig(context)
        } catch (e: IOException) {
            Log.w(TAG, "Cannot read config, starting from defaults", e)
            defaultConfig(context)
        }
    }

    // Only the latest config is written: one that a switch has replaced was saved by the switch.
    private fun save(config: PanelConfig) {
        synchronized(lock) {
            if (config === _config.value) write(file(_active.value), config)
        }
    }

    private fun write(file: AtomicFile, config: PanelConfig) {
        var stream: FileOutputStream? = null
        try {
            file.baseFile.parentFile?.mkdirs()
            stream = file.startWrite()
            stream.write(config.toJson().encodeToByteArray())
            file.finishWrite(stream)
        } catch (e: IOException) {
            stream?.let(file::failWrite)
            Log.w(TAG, "Cannot save config", e)
        }
    }

    companion object {
        private const val TAG = "ConfigStore"
        private const val SAVE_DELAY_MS = 500L

        @Volatile
        private var instance: ConfigStore? = null

        fun get(context: Context): ConfigStore =
            instance ?: synchronized(this) {
                instance ?: ConfigStore(context.applicationContext).also { instance = it }
            }

        fun newId(): String = UUID.randomUUID().toString()

        private fun defaultConfig(context: Context): PanelConfig {
            fun actions(vararg actions: Action) = actions.map { ActionItem(context.getString(it.labelRes), it) }
            val shortcuts = actions(Action.NOTIFICATIONS, Action.QUICK_SETTINGS, Action.SCREENSHOT, Action.LOCK_SCREEN)
            val switches = actions(Action.WIFI, Action.BLUETOOTH, Action.FLASHLIGHT, Action.AUTO_ROTATE)
            return PanelConfig(
                pages = listOf(
                    Page(
                        listOf(
                            GridCard(newId(), context.getString(R.string.default_card_apps)),
                            GridCard(newId(), context.getString(R.string.default_card_shortcuts), shortcuts),
                            GridCard(newId(), context.getString(R.string.default_card_switches), switches),
                            SystemCard(newId(), context.getString(R.string.system_battery), SystemKind.BATTERY),
                            NoteCard(newId(), context.getString(R.string.default_card_note)),
                        ),
                    ),
                ),
            )
        }
    }
}
