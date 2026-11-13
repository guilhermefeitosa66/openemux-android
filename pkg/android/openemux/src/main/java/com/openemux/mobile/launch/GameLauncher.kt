package com.openemux.mobile.launch

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import android.view.WindowManager
import androidx.core.content.res.ResourcesCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.openemux.mobile.data.AchievementsStore
import com.openemux.mobile.data.BiosDef
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.Covers
import com.openemux.mobile.data.Labels
import com.openemux.mobile.data.Library
import com.openemux.mobile.data.NdsLayouts
import com.openemux.mobile.data.Rom
import com.openemux.mobile.data.SaveSlots
import com.openemux.mobile.data.Setup
import com.openemux.mobile.data.SkinStore
import com.openemux.mobile.data.SkinVariant
import com.openemux.mobile.data.configKeys
import com.openemux.mobile.game.GameActivity
import com.openemux.mobile.game.GameExtras
import com.openemux.mobile.game.Gamepads
import com.openemux.mobile.game.TouchControls
import com.openemux.mobile.game.TouchControlsStore
import com.openemux.mobile.ui.AppWindow
import com.openemux.mobile.ui.ControllerLayoutPref
import com.openemux.mobile.ui.Fold
import com.openemux.mobile.ui.PauseComboPref
import com.openemux.mobile.ui.gamepadSkin
import com.retroarch.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** Composes the cartridge art the way the desktop does: label under the frame. */
object CartArt {
    /** The frame drawable per console and colour, looked up by name once (issue 918): a tile never asks the resources again. */
    private val frames = java.util.concurrent.ConcurrentHashMap<String, Int>()

    fun frameRes(context: Context, console: Console, color: String): Int {
        val name = console.frame?.name ?: return 0
        return frames.getOrPut("$name/$color") {
            val res = context.resources
            if (color != "default") {
                val id = res.getIdentifier("${name}_$color", "drawable", context.packageName)
                if (id != 0) return@getOrPut id
            }
            res.getIdentifier(name, "drawable", context.packageName)
        }
    }

    fun loadLabel(file: File?): Bitmap? = file?.let { BitmapFactory.decodeFile(it.path) }

    /** A box-shaped card with the title, for a console with no frame and no box art. */
    fun titleCard(context: Context, title: String): Bitmap {
        val out = Bitmap.createBitmap(360, 500, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(36, 36, 44) }
        c.drawRoundRect(RectF(0f, 0f, 360f, 500f), 24f, 24f, p)
        p.color = Color.rgb(163, 163, 178)
        p.typeface = ResourcesCompat.getFont(context, R.font.grotesk_bold) ?: Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = 34f
        c.drawText(title.take(22), 180f, 250f, p)
        return out
    }

    fun compose(context: Context, console: Console, colorId: String, label: Bitmap?, title: String): Bitmap {
        val frame = (ResourcesCompat.getDrawable(context.resources, frameRes(context, console, colorId), null) as BitmapDrawable).bitmap
        val out = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val l = console.frame!!.label
        val dst = RectF(l.left * frame.width, l.top * frame.height, (l.left + l.width) * frame.width, (l.top + l.height) * frame.height)
        if (label != null) {
            // Crop-to-fill, centred, like the desktop compositor.
            val target = dst.width() / dst.height()
            val srcW: Int
            val srcH: Int
            if (label.width.toFloat() / label.height > target) {
                srcH = label.height; srcW = (label.height * target).toInt()
            } else {
                srcW = label.width; srcH = (label.width / target).toInt()
            }
            val sx = (label.width - srcW) / 2
            val sy = (label.height - srcH) / 2
            c.drawBitmap(label, Rect(sx, sy, sx + srcW, sy + srcH), dst, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(222, 219, 212) }
            c.drawRect(dst, p)
            p.color = Color.rgb(40, 40, 46)
            p.typeface = ResourcesCompat.getFont(context, R.font.grotesk_bold) ?: Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            p.textSize = dst.height() * 0.16f
            val words = title.split(" ")
            val lines = mutableListOf<String>()
            var line = ""
            for (w in words) {
                val next = if (line.isEmpty()) w else "$line $w"
                if (p.measureText(next) > dst.width() * 0.86f && line.isNotEmpty()) { lines += line; line = w } else line = next
            }
            if (line.isNotEmpty()) lines += line
            val shown = lines.take(3)
            val lh = p.textSize * 1.15f
            var y = dst.centerY() - lh * (shown.size - 1) / 2f - (p.ascent() + p.descent()) / 2f
            for (s in shown) { c.drawText(s, dst.centerX(), y, p); y += lh }
        }
        c.drawBitmap(frame, 0f, 0f, null)
        return out
    }
}

class GameLauncher(
    private val context: Context,
    private val library: Library,
    private val covers: Covers,
    private val labels: Labels,
    private val achievements: AchievementsStore,
    private val setup: Setup,
) {
    private val paths = library.paths
    /** The skins and the cartridge pictures drawn for launches before, so a launch like one before draws nothing (issue 917). */
    private val skinCache = SkinCache(File(paths.launch, "skins"))
    private val cartCache = CartCache(File(paths.launch, "carts"))

    private val _stage = MutableStateFlow<LaunchStage?>(null)
    /** What the launch under way is doing, for the overlay (issue 923); null between launches. */
    val stage: StateFlow<LaunchStage?> = _stage.asStateFlow()

    /** The first required BIOS [console] lacks, if any: asked before a launch (issue 923). Reads the disk. */
    fun missingBios(console: Console): BiosDef? = missingBios(console, paths.system)

    /**
     * How the session launched at [startedAt] ended, with the tail of the engine's log, or null when nothing needs
     * saying (issue 923): the markers the game activity leaves next to its config say whether the engine ever drew
     * and whether the player quit through the sheet. Reads the disk.
     */
    fun sessionOutcome(startedAt: Long): Pair<LaunchOutcome, String>? {
        val dir = File(paths.launch, "current")
        val outcome = launchOutcome(
            started = File(dir, SessionMarks.STARTED).exists(), quit = File(dir, SessionMarks.QUIT).exists(),
            elapsedMs = System.currentTimeMillis() - startedAt,
        ) ?: return null
        return outcome to logTail(File(dir, SessionMarks.LOG))
    }

    /** Shaders, copied out of the APK once per install; cores and core info come from the setup. */
    private fun prepareEngine() {
        val stamp = File(paths.internal, "engine.stamp")
        val version = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        if (stamp.exists() && stamp.readText() == version) return
        copyAssetTree("shaders", paths.shaders)
        stamp.writeText(version)
    }

    private fun copyAssetTree(asset: String, target: File) {
        val children = context.assets.list(asset).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(asset).use { input -> target.outputStream().use { input.copyTo(it) } }
            return
        }
        target.mkdirs()
        children.forEach { copyAssetTree("$asset/$it", File(target, it)) }
    }

    /** What the cartridge picture is composed from: a change in any of it is a new picture. */
    private fun cartKey(rom: Rom): String {
        val label = labels.found.value[rom.key]
        val art = if (rom.console.frame == null) "cover=${covers.urlFor(rom)}" else "label=${label?.path}:${label?.length()}:${label?.lastModified()}"
        return "rom=${rom.key}\ntitle=${rom.title}\ncolor=${library.effectiveColor(rom)}\n$art\napp=$appVersion"
    }

    /** The install time of this build: the bundled skins, the frames and the renderers change with it. */
    private val appVersion: Long by lazy { context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime }

    /**
     * Everything the skin drawing reads, one line each: the same text means the same skin (SkinCache). The player's
     * skin goes in with its files' sizes and times, the NDS layouts with the saved file, the app with its install time.
     */
    internal fun skinKey(
        console: Console, size: SkinSize, density: Float, hardcore: Boolean, coreId: String, first: String?, fold: Fold?,
        gamepad: Boolean, variant: SkinVariant, custom: String?,
    ): SkinCache.Key = SkinCache.Key(
        listOf(
            "console=${console.id}", "size=$size", "density=$density", "hardcore=$hardcore", "core=$coreId", "first=$first",
            "fold=$fold", "gamepad=$gamepad", "variant=${variant.id}",
            "custom=$custom:${SkinCache.signature(custom?.let { SkinStore(paths.base).file(console, it) })}",
            "nds=${if (console == Console.NDS) NdsLayouts.savedText(context.filesDir) else ""}",
            "app=$appVersion",
        ).joinToString("\n"),
    )

    suspend fun cartBitmap(rom: Rom): Bitmap {
        if (rom.console.frame == null) {
            // No cartridge art for this console: the box art stands in, as on the desktop.
            val request = ImageRequest.Builder(context).data(covers.urlFor(rom)).allowHardware(false).build()
            val box = ((context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
            return box ?: withContext(Dispatchers.Default) { CartArt.titleCard(context, rom.title) }
        }
        return withContext(Dispatchers.Default) {
            val label = CartArt.loadLabel(labels.found.value[rom.key])
            CartArt.compose(context, rom.console, library.effectiveColor(rom), label, rom.title)
        }
    }

    /** Starts the game from [from], in the library's own task, so it opens inside the app. */
    suspend fun launch(from: Activity, rom: Rom, resume: Boolean, slot: Int? = null) {
        _stage.value = LaunchStage.Controls
        try {
            launchStaged(from, rom, resume, slot)
        } finally {
            _stage.value = null
        }
    }

    private suspend fun launchStaged(from: Activity, rom: Rom, resume: Boolean, slot: Int?) {
        val started = SystemClock.uptimeMillis()
        val core = library.effectiveCore(rom)
        // The console's core comes down the first time one of its games is launched (issue 922), cancellable like the rest.
        if (!setup.coreInstalled(core)) {
            _stage.value = LaunchStage.Download(core.name, 0, 0)
            setup.ensureCore(core) { bytes, total -> _stage.value = LaunchStage.Download(core.name, bytes, total) }
            _stage.value = LaunchStage.Controls
        }
        // The picture kept from a launch of this game before, else composed and kept now.
        val cartKey = cartKey(rom)
        var cartKept = cartCache.lookup(cartKey)
        val cart = if (cartKept == null) {
            Trace.beginSection("OpenEmux cart")
            try { cartBitmap(rom) } finally { Trace.endSection() }
        } else null
        val cheevos = achievements.state.value
        // Hardcore refuses loading a state: start fresh rather than have the engine refuse it.
        val hardcore = cheevos.hardcoreActive
        // The fold as the library's window saw it last: the skin draws the tabletop shape for it. Read, never waited
        // for (issue 917); a fold the window reports later redraws the skin in game.
        val fold = AppWindow.fold
        val intent = withContext(Dispatchers.IO) {
            prepareEngine()
            val dir = File(paths.launch, "current").apply { deleteRecursively(); mkdirs() }

            val cartFile = cartKept ?: cartCache.keep(cartKey) { f -> f.outputStream().use { cart!!.compress(Bitmap.CompressFormat.PNG, 100, it) } }

            // The largest window: right full screen; the game redraws it for split screen or free form.
            val metrics = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).maximumWindowMetrics
            val size = SkinSize.of(metrics)
            // A skin picked in game since the library last resumed is this console's now.
            runCatching { library.absorbSkinPicks() }
            // So is anchor mode turned on or off in game.
            runCatching { library.absorbAnchorPicks() }
            val anchor = library.anchor(rom.console)
            val skinVariant = library.skinVariant(rom.console)
            val customSkin = library.customSkin(rom.console)
            val density = context.resources.displayMetrics.density
            // The skin is drawn upright: the fold goes with it.
            val upright = SkinGeometry.portraitFold(fold, metrics.bounds.width(), metrics.bounds.height())
            val landscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            // A gamepad opens the gamepad layout, unless the player brought the touch controls back for it (issue 865)
            // or the interface mode is Touch screen (issue 873); with none, the console's pick of it (issue 865).
            val pads = Gamepads.pads(Gamepads.devices())
            val gamepad = gamepadSkin(
                ControllerLayoutPref.read(context), pads.isNotEmpty(),
                TouchControlsStore.of(context.filesDir).forced(TouchControls.keys(pads)),
                rom.console.id in TouchControlsStore.consoles(context.filesDir),
            )
            // A package skin opens on its page for the orientation the game starts in (the NDS's saved layout there).
            val first = "landscape".takeIf { landscape }
            val key = skinKey(rom.console, size, density, hardcore, core.id, first, upright, gamepad, skinVariant, customSkin)
            val kept = skinCache.lookup(key) { orientation, id -> NdsLayouts.find(orientation, id) }
            val drawn = kept ?: skinCache.render(key) { outDir ->
                Trace.beginSection("OpenEmux skin")
                try {
                    GameSkin.render(
                        context, paths.base, rom.console, size, outDir, density, hardcore, core.id,
                        first = first, fold = upright, gamepad = gamepad, variant = skinVariant, custom = customSkin,
                    )
                } finally {
                    Trace.endSection()
                }
            }
            Log.i(TAG, "launch: skin ${if (kept != null) "kept" else "drawn"}, cartridge ${if (cartKept != null) "kept" else "composed"}, ${SystemClock.uptimeMillis() - started} ms so far")
            val skin = drawn.file
            val ndsLayouts = drawn.ndsLayouts

            // A shader picked in game since the library last resumed is this ROM's now.
            library.absorbShaderPicks()
            val shader = library.effectiveShader(rom)
            shader.preset?.let { preset ->
                File(dir, "global.glslp").writeText("#reference \"${File(paths.shaders, preset).absolutePath}\"\n")
            }

            File(dir, "core-options.cfg").writeText(
                "mgba_skip_bios = \"ON\"\n" +
                    "mgba_use_bios = \"ON\"\n" +
                    // The skins draw the 6-button pad; Genesis Plus GX gets it from its port device (buildConfig).
                    "picodrive_input1 = \"6 button pad\"\n" +
                    // The skin opens on the saved layout of the orientation the game starts in (the engine turns to the
                    // landscape page at once), so the first frame is drawn right; the activity follows the overlay from there.
                    (ndsLayouts?.let { "desmume_screens_layout = \"${ndsLaunchLayout(it, landscape).value}\"\n" } ?: "") +
                    (if (rom.console == Console.NDS && drawn.packaged && ndsLayouts == null) "desmume_screens_layout = \"$PACKAGE_NDS_LAYOUT\"\n" else "")
            )

            listOf(paths.states(rom.console), paths.saves(rom.console), paths.system, paths.screenshots, paths.playtime)
                .forEach { it.mkdirs() }
            // A numbered state opened from the game's screen is its current slot from now on; the auto state leaves it (issue 857).
            // Not in hardcore, where the engine is not asked to load it (LOAD_SLOT below).
            if (slot != null && !hardcore) SaveSlots.setCurrent(paths.states(rom.console), rom.baseName, slot)
            // The controller combination that opens the pause sheet (issue 924).
            val pauseCombo = PauseComboPref.read(context)
            val config = File(dir, "retroarch.cfg")
            config.writeText(buildConfig(rom, core.id, resume && !hardcore, dir, skin, cheevos.configKeys(), hardcore, anchor, pauseCombo))

            library.updateRom(rom.key) { it.copy(lastPlayed = System.currentTimeMillis()) }

            Intent(context, GameActivity::class.java).apply {
                putExtra("ROM", rom.file.absolutePath)
                putExtra("LIBRETRO", File(paths.cores, core.fileName).absolutePath)
                putExtra("CONFIGFILE", config.absolutePath)
                putExtra("IME", "com.android.inputmethod.latin/.LatinIME")
                putExtra(GameExtras.TITLE, rom.title)
                putExtra(GameExtras.CONSOLE, rom.console.id)
                putExtra(GameExtras.CART, cartFile.absolutePath)
                putExtra(GameExtras.STATES_DIR, paths.states(rom.console).absolutePath)
                putExtra(GameExtras.ROM_BASE, rom.baseName)
                putExtra(GameExtras.PLAYTIME, library.playtimeFile(rom).absolutePath)
                putExtra(GameExtras.SHADERS_DIR, paths.shaders.absolutePath)
                putExtra(GameExtras.SHADER, shader.id)
                putExtra(GameExtras.ROM_KEY, rom.key)
                putExtra(GameExtras.SHADER_PICKS, paths.shaderPicks.absolutePath)
                putExtra(GameExtras.SKIN_PICKS, paths.skinPicks.absolutePath)
                putExtra(GameExtras.ANCHOR, anchor)
                putExtra(GameExtras.ANCHOR_PICKS, paths.anchorPicks.absolutePath)
                putExtra(GameExtras.PAUSE_COMBO, pauseCombo)
                putExtra(GameExtras.SKIN_DIR, skin.parentFile!!.absolutePath)
                putExtra(GameExtras.CORE_NAME, core.name)
                putExtra(GameExtras.HARDCORE, hardcore)
                putExtra(GameExtras.LAUNCH_DIR, dir.absolutePath)
                putExtra(GameExtras.SKIN_SIZE, size.toString())
                putExtra(GameExtras.SKINS_BASE, paths.base.absolutePath)
                putExtra(GameExtras.CORE_ID, core.id)
                putExtra(GameExtras.SKIN_VARIANT, skinVariant.id)
                putExtra(GameExtras.SKIN_CUSTOM, customSkin)
                if (slot != null && !hardcore) putExtra(GameExtras.LOAD_SLOT, slot)
            }
        }
        // A launch cancelled from the overlay stops here: the engine is never started.
        currentCoroutineContext().ensureActive()
        _stage.value = LaunchStage.Core(core.name)
        from.startActivity(intent, ActivityOptions.makeCustomAnimation(from, R.anim.game_enter, R.anim.hold).toBundle())
    }

    private fun buildConfig(
        rom: Rom, coreId: String, resume: Boolean, dir: File, skin: File, cheevos: Map<String, String>, hardcore: Boolean, anchor: Boolean,
        pauseCombo: String,
    ): String {
        val settings = linkedMapOf(
            "video_driver" to "gl",
            "menu_driver" to "glui",
            "video_shader_enable" to "true",
            "video_shader_dir" to paths.shaders.absolutePath,
            "rgui_config_directory" to dir.absolutePath,
            "video_smooth" to "false",
            "video_scale_integer" to "false",
            "video_viewport_bias_portrait_x" to "0.500000",
            "video_viewport_bias_portrait_y" to "0.000000",
            "video_notch_write_over_enable" to "false",
            "input_overlay_enable" to "true",
            // The skin's turbo toggle: once on, every held button repeats (engine opt-in).
            // Every session starts with it off, as the strip's "on" pill (skin.cfg _lit_by) shows.
            "input_turbo_locked" to "true",
            "input_turbo_mode" to "0",
            "input_turbo_enable" to "false",
            // The skin's anchor key (issue 841): the mode starts as the console's settings have it; the engine reports
            // each change (GameActivity.onToggleStates), which the console keeps.
            "input_overlay_anchor" to anchor.toString(),
            "input_overlay" to skin.absolutePath,
            "input_overlay_auto_rotate" to "true",
            "input_overlay_opacity" to "1.000000",
            "input_overlay_auto_scale" to "false",
            "input_overlay_hide_in_menu" to "true",
            // The skin switches to its gamepad layout instead (GameActivity): the app controls stay on screen.
            "input_overlay_hide_when_gamepad_connected" to "false",
            "input_overlay_show_inputs" to "0",
            // A controller pauses without a touch (issue 924): the engine's menu toggle opens the pause sheet.
            "input_menu_toggle_gamepad_combo" to PauseComboPref.engineCombo(pauseCombo),
            "libretro_directory" to paths.cores.absolutePath,
            "libretro_info_path" to paths.info.absolutePath,
            "system_directory" to paths.system.absolutePath,
            "savestate_directory" to paths.states(rom.console).absolutePath,
            "savefile_directory" to paths.saves(rom.console).absolutePath,
            "screenshot_directory" to paths.screenshots.absolutePath,
            "sort_savestates_enable" to "false",
            "sort_savefiles_enable" to "false",
            "sort_savestates_by_content_enable" to "false",
            "sort_savefiles_by_content_enable" to "false",
            "sort_screenshots_by_content_enable" to "false",
            "savestates_in_content_dir" to "false",
            "savefiles_in_content_dir" to "false",
            // A hardcore session starts fresh: saving on quit would overwrite the softcore auto state.
            "savestate_auto_save" to (!hardcore).toString(),
            "savestate_auto_load" to resume.toString(),
            "savestate_thumbnail_enable" to "true",
            // Save and Load on the strip and the controller act on the ROM's current slot, as the pause menu does (issue 857).
            "state_slot" to SaveSlots.current(paths.states(rom.console), rom.baseName).toString(),
            // RetroArch's runtime log would put back the slot the last session ended on over state_slot when the content
            // loads; the app keeps its own playtime, so the log is off and the ROM's current slot is the one the engine uses.
            "content_runtime_log" to "false",
            "content_runtime_log_aggregate" to "false",
            "global_core_options" to "true",
            "core_options_path" to File(dir, "core-options.cfg").absolutePath,
            // The engine's log next to the config, for a session that ends on its own (issue 923).
            "log_to_file" to "true",
            "log_to_file_timestamp" to "false",
            "log_dir" to dir.absolutePath,
            "frontend_log_level" to "1",
            "libretro_log_level" to "1",
            "network_cmd_enable" to "true",
            "network_cmd_port" to "55355",
            "config_save_on_exit" to "false",
            // Every game starts with sound: the pause sheet's Audio tile assumes it until the engine reports a change.
            "audio_mute_enable" to "false",
            // The app ships its own shaders and core info; extracting the APK would also save the config with a toast.
            "bundle_assets_extract_enable" to "false",
            "confirm_quit" to "false",
            "auto_overrides_enable" to "false",
            "auto_remaps_enable" to "false",
            "menu_show_load_content_animation" to "false",
            // RetroArch's own menu, opened from the pause sheet for advanced settings: Close Content quits like the
            // sheet's Quit (auto save, back to the library) instead of leaving an empty menu with no game.
            "quit_on_close_content" to "1",
            // Entries that would load other content or cores, update, restart or switch config files: a session the app
            // does not manage. Quit RetroArch stays: it is the sheet's Quit in effect.
            "menu_show_load_core" to "false",
            "menu_show_load_content" to "false",
            "menu_show_load_disc" to "false",
            "menu_show_dump_disc" to "false",
            "menu_show_online_updater" to "false",
            "menu_show_core_updater" to "false",
            "menu_show_restart_retroarch" to "false",
            "menu_show_configurations" to "false",
            "content_show_history" to "false",
            "content_show_playlists" to "false",
            "content_show_favorites" to "false",
            "content_show_explore" to "false",
            "notification_show_autoconfig" to "false",
            "notification_show_remap_load" to "false",
            "notification_show_config_override_load" to "false",
            "notification_show_set_initial_disk" to "false",
            "notification_show_autoconfig_fails" to "false",
            "notification_show_cheats_applied" to "false",
            "notification_show_patch_applied" to "false",
            "notification_show_refresh_rate" to "false",
            "notification_show_netplay_extra" to "false",
            "notification_show_disk_control" to "false",
            "notification_show_screenshot" to "false",
            "notification_show_screenshot_flash" to "0",
            "notification_show_when_menu_is_alive" to "false",
            "notification_show_autoload_state" to "false",
            "notification_show_fast_forward" to "true",
            "video_font_enable" to "true",
            "fps_show" to "false",
            // rcheevos keeps its badge cache under the thumbnails directory.
            "thumbnails_directory" to File(paths.internal, "thumbnails").absolutePath,
        )
        settings.putAll(cheevos)
        // RetroArch still fast-forwards in hardcore; the skin and the pause sheet leave it out, and so do the keys.
        // It refuses slow motion there already: its keys go too, so no binding even reaches it.
        if (hardcore) HARDCORE_UNBOUND_KEYS.forEach { settings[it] = "nul" }
        // The PS skin has both sticks: Beetle PSX and PCSX ReARMed read them as a DualShock.
        if (rom.console == Console.PS) settings["input_libretro_device_p1"] = "517"
        // Genesis Plus GX only gives a pad six buttons when the ROM header asks; the MD skins draw X, Y, Z and MODE
        // for every game, so pin its MD Joypad 6 Button. PicoDrive reads picodrive_input1 instead.
        if ((rom.console == Console.MD || rom.console == Console.MCD) && coreId == "genesis_plus_gx") {
            settings["input_libretro_device_p1"] = "513"
        }
        // atari800 only switches to its 5200 joystick when the frontend asks for that port device; the default joypad moves Fire 2 to B.
        if (rom.console == Console.A5200) settings["input_libretro_device_p1"] = "769"
        return settings.entries.joinToString("\n") { (k, v) -> "$k = \"$v\"" } + "\n"
    }

    private companion object {
        const val TAG = "OpenEmuxLaunch"
    }
}
