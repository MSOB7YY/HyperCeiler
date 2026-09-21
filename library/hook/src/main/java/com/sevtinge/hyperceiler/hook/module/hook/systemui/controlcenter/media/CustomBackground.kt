/*
 * This file is part of HyperCeiler.

 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.

 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.

 * Copyright (C) 2023-2025 HyperCeiler Contributions
 */
package com.sevtinge.hyperceiler.hook.module.hook.systemui.controlcenter.media

import android.app.WallpaperColors
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import com.sevtinge.hyperceiler.hook.module.base.BaseHook
import com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.MediaControlBgFactory.conColorScheme2
import com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.MediaControlBgFactory.conColorScheme3
import com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.MediaControlBgFactory.defaultColorConfig
import com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.MediaControlBgFactory.enumStyleContent
import com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.MediaControlBgFactory.fldColorSchemeAccent1
import com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.MediaControlBgFactory.fldColorSchemeAccent2
import com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.MediaControlBgFactory.fldColorSchemeNeutral1
import com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.MediaControlBgFactory.fldColorSchemeNeutral2
import com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.MediaControlBgFactory.fldTonalPaletteAllShades
import com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.MediaControlBgFactory.getScaledBackground
import com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.MediaControlBgFactory.getWallpaperColor
import com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.PublicClass.miuiMediaControlPanel
import com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.PublicClass.miuiMediaViewControllerImpl
import com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.PublicClass.playerTwoCircleView
import com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.drawable.MediaControlBgDrawable
import com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.mediabackground.AndroidTenProcessor
import com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.mediabackground.BgProcessor
import com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.mediabackground.BlurredCoverProcessor
import com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.mediabackground.CoverArtProcessor
import com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.mediabackground.LinearGradientProcessor
import com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.mediabackground.MediaViewColorConfig
import com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.mediabackground.RadialGradientProcessor
import com.sevtinge.hyperceiler.hook.utils.devicesdk.isAndroidVersion
import com.sevtinge.hyperceiler.hook.utils.devicesdk.isMoreAndroidVersion
import com.sevtinge.hyperceiler.hook.utils.devicesdk.DisplayUtils.dp2px
import com.sevtinge.hyperceiler.hook.utils.getBooleanField
import com.sevtinge.hyperceiler.hook.utils.getObjectFieldOrNullAs
import com.sevtinge.hyperceiler.hook.utils.getValueByField
import com.sevtinge.hyperceiler.hook.utils.replaceMethod
import com.sevtinge.hyperceiler.hook.utils.setObjectField
import io.github.kyuubiran.ezxhelper.core.finder.ConstructorFinder.`-Static`.constructorFinder
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil.loadClassOrNull
import io.github.kyuubiran.ezxhelper.xposed.dsl.HookFactory.`-Static`.createAfterHook
import io.github.kyuubiran.ezxhelper.xposed.dsl.HookFactory.`-Static`.createBeforeHook
import io.github.kyuubiran.ezxhelper.xposed.dsl.HookFactory.`-Static`.createHook
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.WeakHashMap

// https://github.com/HowieHChen/XiaomiHelper/blob/72d6a928358f7de7a3b3e872f18acaa83f1cfe33/app/src/main/kotlin/dev/lackluster/mihelper/hook/rules/systemui/media/CustomBackground.kt
object CustomBackground : BaseHook() {
    // background:
    // 0 -> Default;
    // 1 -> Art;
    // 2 -> Blurred cover;
    // 3 -> AndroidNewStyle;
    // 4 -> AndroidOldStyle
    // 5 -> HyperOS Blur
    // 6 -> Android 10
    private val backgroundStyle by lazy {
        mPrefsMap.getStringAsInt("system_ui_control_center_media_control_background_mode", 0)
    }
    private lateinit var processor: BgProcessor

    private val actionOpacity by lazy {
        mPrefsMap.getInt("system_ui_control_center_media_control_action_opacity", 100).coerceIn(10, 100)
    }
    private val useDisplayMetadata by lazy {
        mPrefsMap.getBoolean("system_ui_control_center_media_control_display_metadata")
    }
    // 0 -> hidden; 1 -> in place of the device switch button; 2 -> second line under the artist;
    // 3 -> after the title; 4 -> before the title
    private val descriptionPosition by lazy {
        mPrefsMap.getStringAsInt("system_ui_control_center_media_control_display_description", 0)
    }
    // ConstraintSet 在部分机型上不生效，这里把视图直接摘出布局
    private val hideAlbumCover by lazy {
        mPrefsMap.getStringAsInt("system_ui_control_center_media_control_media_album_mode", 0) == 2
    }
    private val hideSeamless by lazy {
        mPrefsMap.getBoolean("system_ui_control_center_media_control_media_button_hide_seamless")
    }
    private val textPaddingLeft by lazy {
        dp2px(mPrefsMap.getInt("system_ui_control_center_media_control_text_padding_left", 0).toFloat()).toFloat()
    }
    private val textPaddingTop by lazy {
        dp2px(mPrefsMap.getInt("system_ui_control_center_media_control_text_padding_top", 0).toFloat()).toFloat()
    }
    private val actionSpacing by lazy {
        mPrefsMap.getInt("system_ui_control_center_media_control_action_spacing", 100).coerceIn(20, 100)
    }
    private val hasLayoutTweaks by lazy {
        hideAlbumCover || textPaddingLeft != 0f || textPaddingTop != 0f || actionSpacing != 100
    }
    private val compactTimeLabels by lazy {
        mPrefsMap.getBoolean("system_ui_control_center_media_control_compact_time")
    }
    private val emphasizeText by lazy {
        mPrefsMap.getBoolean("system_ui_control_center_media_control_emphasize_text")
    }

    // 每个媒体卡片各自持有状态，否则多个会话会互相覆盖背景与配色
    private val panelStates = Collections.synchronizedMap(WeakHashMap<Any, PanelState>())

    private var lastWidth = 0
    private var lastHeight = 0

    private val isAndroidB by lazy {
        isMoreAndroidVersion(36)
    }
    private val isAndroidV by lazy {
        isAndroidVersion(35)
    }

    override fun init() {
        processor = when (backgroundStyle) {
            1 -> CoverArtProcessor()
            2 -> BlurredCoverProcessor()
            3 -> RadialGradientProcessor()
            4 -> LinearGradientProcessor()
            6 -> AndroidTenProcessor()
            else -> return
        }

        if (!isAndroidB) {
            if (isAndroidV) {
                loadClassOrNull("com.android.systemui.media.controls.ui.controller.MediaViewController")!!.methodFinder()
                    .filterByName("resetLayoutResource")
                    .first()
                    .replaceMethod {
                        null
                    }
            }

            playerTwoCircleView?.apply {
                if (isAndroidV) {
                    constructorFinder().filterByParamCount(4)
                } else {
                    constructorFinder().filterByParamCount(3)
                }.first().createAfterHook { param ->
                    param.thisObject.getObjectFieldOrNullAs<Paint>("mPaint1")?.alpha = 0
                    param.thisObject.getObjectFieldOrNullAs<Paint>("mPaint2")?.alpha = 0
                    param.thisObject.setObjectField("mRadius", 0.0f)

                }

                methodFinder().filterByName("setBackground")
                    .first()
                    .createHook {
                        returnConstant(null)
                    }

                methodFinder().filterByName("setPaintColor")
                    .first()
                    .createHook {
                        returnConstant(null)
                    }
            }

            miuiMediaControlPanel!!.apply {
                // com.android.systemui.media.controls.ui.MediaControlPanel
                /*superclass!!.methodFinder().filterByName("attachPlayer")
                    .first()
                    .createAfterHook { param ->
                        val mMediaViewHolder =
                            param.thisObject.getObjectField("mMediaViewHolder")
                                ?: return@createAfterHook

                        initMediaViewHolder(mMediaViewHolder)
                    }*/


                if (isAndroidV) {
                    // Android 16 在其 com.android.systemui.media.controls.ui.controller.MediaControlPanel 类可见
                    // Android 15 在其超类中抽象了此方法，并在继承中重写了此方法
                    // Android 14 查无此方法
                    methodFinder().filterByName("onDestroy")
                        .first()
                        .createAfterHook { param ->
                            panelStates.remove(param.thisObject)
                        }

                    methodFinder().filterByName("setPlayerBg")
                        .first()
                        .replaceMethod {
                            null
                        }

                    methodFinder().filterByName("setForegroundColors")
                        .first()
                        .replaceMethod {
                            null
                        }
                }


                methodFinder().filterByName("bindPlayer")
                    .first()
                    .createAfterHook { param ->
                        val context = param.thisObject.getObjectFieldOrNullAs<Context>("mContext")
                            ?: return@createAfterHook
                        val mediaData = param.args[0] ?: return@createAfterHook
                        val artwork = mediaData.getObjectFieldOrNullAs<Icon>("artwork")
                        val packageName = mediaData.getObjectFieldOrNullAs<String>("packageName")
                            ?: return@createAfterHook
                        val state = stateOf(param.thisObject)
                        val isArtWorkUpdate =
                            param.thisObject.getBooleanField("mIsArtworkUpdate")
                                || state.pkgName != packageName
                        val mMediaViewHolder =
                            getValueByField(param.thisObject, "mMediaViewHolder") ?: return@createAfterHook
                        val holder = initMediaViewHolder(mMediaViewHolder) ?: return@createAfterHook

                        updateBackground(context, state, mediaData, isArtWorkUpdate, artwork, packageName, holder)
                    }

            }
        } else {
            miuiMediaViewControllerImpl?.apply {
                methodFinder().filterByName("updateMediaBackground")
                    .first()
                    .replaceMethod {
                        null
                    }

                methodFinder().filterByName("detach")
                    .first()
                    .createBeforeHook { param ->
                        panelStates.remove(param.thisObject)
                    }

                methodFinder().filterByName("updateForegroundColors")
                    .first()
                    .replaceMethod {
                        null
                    }

                methodFinder().filterByName("bindMediaData")
                    .first()
                    .createAfterHook { param ->
                        val context = param.thisObject.getObjectFieldOrNullAs<Context>("context")
                            ?: return@createAfterHook
                        val mediaData = param.args[0] ?: return@createAfterHook
                        val artwork = mediaData.getObjectFieldOrNullAs<Icon>("artwork")
                        val packageName = mediaData.getObjectFieldOrNullAs<String>("packageName")
                            ?: return@createAfterHook
                        val state = stateOf(param.thisObject)
                        val isArtWorkUpdate =
                            param.thisObject.getBooleanField("isArtWorkUpdate")
                                || state.pkgName != packageName
                        val mMediaViewHolder = getValueByField(param.thisObject, "holder")
                            ?: return@createAfterHook
                        val holder = initMediaViewHolder(mMediaViewHolder) ?: return@createAfterHook

                        updateBackground(context, state, mediaData, isArtWorkUpdate, artwork, packageName, holder)
                    }

            }
        }

    }

    private fun initMediaViewHolder(mMediaViewHolder: Any): MiuiMediaViewHolder? {
        val mediaBg = mMediaViewHolder.getObjectFieldOrNullAs<ImageView>("mediaBg") ?: return null
        val titleText = mMediaViewHolder.getObjectFieldOrNullAs<TextView>("titleText") ?: return null
        val artistText = mMediaViewHolder.getObjectFieldOrNullAs<TextView>("artistText") ?: return null
        val seamlessIcon = mMediaViewHolder.getObjectFieldOrNullAs<ImageView>("seamlessIcon") ?: return null
        val seamlessText = mMediaViewHolder.getObjectFieldOrNullAs<TextView>("seamlessText")
        val seamless = mMediaViewHolder.getObjectFieldOrNullAs<View>("seamless")
        val appIcon = mMediaViewHolder.getObjectFieldOrNullAs<ImageView>("appIcon")
        val action0 = mMediaViewHolder.getObjectFieldOrNullAs<ImageButton>("action0") ?: return null
        val action1 = mMediaViewHolder.getObjectFieldOrNullAs<ImageButton>("action1") ?: return null
        val action2 = mMediaViewHolder.getObjectFieldOrNullAs<ImageButton>("action2") ?: return null
        val action3 = mMediaViewHolder.getObjectFieldOrNullAs<ImageButton>("action3") ?: return null
        val action4 = mMediaViewHolder.getObjectFieldOrNullAs<ImageButton>("action4") ?: return null
        val seekBar = mMediaViewHolder.getObjectFieldOrNullAs<SeekBar>("seekBar") ?: return null
        val elapsedTimeView = mMediaViewHolder.getObjectFieldOrNullAs<TextView>("elapsedTimeView") ?: return null
        val totalTimeView = mMediaViewHolder.getObjectFieldOrNullAs<TextView>("totalTimeView") ?: return null
        // OS 3.0.0.14.WOCCNXM changed "albumView" to "albumImageView"
        val albumView = mMediaViewHolder.getObjectFieldOrNullAs<ImageView>("albumImageView")
            ?: mMediaViewHolder.getObjectFieldOrNullAs<ImageView>("albumView")
            ?: return null

        return MiuiMediaViewHolder(
            mMediaViewHolder.hashCode(),
            titleText,
            artistText,
            albumView,
            mediaBg,
            seamlessIcon,
            seamlessText,
            seamless,
            appIcon,
            action0,
            action1,
            action2,
            action3,
            action4,
            elapsedTimeView,
            totalTimeView,
            seekBar,
        )
    }

    private fun stateOf(panel: Any): PanelState {
        return panelStates.getOrPut(panel) { PanelState() }
    }

    // MIUI 用可变字体，Typeface.BOLD 常常既拿不到真粗体也不会触发伪粗体
    // 改可见性会被 MIUI 的异步绑定重新打开，直接摘出布局才稳
    private fun View.detach() {
        val group = parent as? ViewGroup ?: return
        group.removeView(this)
    }

    private fun bold(view: TextView) {
        if (view.paint.isFakeBoldText || view.fontVariationSettings == BOLD_WEIGHT) return
        if (!view.setFontVariationSettings(BOLD_WEIGHT)) {
            view.paint.isFakeBoldText = true
            view.invalidate()
        }
    }

    private fun shadow(view: TextView, textColor: Int) {
        val color = if (ColorUtils.calculateLuminance(textColor) > 0.5) SHADOW_DARK else SHADOW_LIGHT
        val radius = view.textSize / 14f
        if (view.shadowRadius != radius || view.shadowColor != color) {
            view.setShadowLayer(radius, 0f, view.textSize / 28f, color)
        }
    }

    private fun TextView.setTextIfChanged(value: CharSequence) {
        if (text?.toString() != value.toString()) text = value
    }

    // 先比对纯文本，绑定频繁但内容多数没变，不必每次都建 Spannable
    private fun TextView.setTitleWithDescription(title: String, description: String) {
        val atStart = descriptionPosition == 4
        val plain =
            if (atStart) description + TITLE_SEPARATOR + title else title + TITLE_SEPARATOR + description
        if (text?.toString() == plain) return

        val builder = SpannableStringBuilder(plain)
        val spanStart = if (atStart) 0 else title.length
        val spanEnd = if (atStart) description.length + TITLE_SEPARATOR.length else plain.length
        builder.setSpan(RelativeSizeSpan(DESCRIPTION_SCALE), spanStart, spanEnd, SPAN_EXCLUSIVE_EXCLUSIVE)
        text = builder
    }

    private fun applyDisplayMetadata(
        context: Context,
        state: PanelState,
        mediaData: Any,
        holder: MiuiMediaViewHolder
    ) {
        val token = mediaData.getObjectFieldOrNullAs<MediaSession.Token>("token") ?: return
        val controller = state.controller?.takeIf { it.sessionToken == token }
            ?: runCatching { MediaController(context, token) }.getOrNull()?.also { state.controller = it }
            ?: return
        val metadata = controller.metadata ?: return
        val subtitle = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)?.takeIf { it.isNotBlank() }
        val description = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)
            ?.takeIf { descriptionPosition != 0 && it.isNotBlank() }
        // 描述只能拼进 DISPLAY_TITLE，否则下次绑定会把上次拼好的标题再拼一遍
        val title = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.takeIf { it.isNotBlank() }
        val titleDescription = description?.takeIf { descriptionPosition >= 3 && title != null }

        if (title != null) {
            if (titleDescription == null) {
                holder.titleText.setTextIfChanged(title)
            } else {
                holder.titleText.setTitleWithDescription(title, titleDescription)
            }
        }

        when {
            descriptionPosition == 2 && description != null ->
                holder.artistText.setTextIfChanged(
                    listOfNotNull(subtitle, description).joinToString(System.lineSeparator())
                )

            subtitle != null -> holder.artistText.setTextIfChanged(subtitle)
        }

        if (descriptionPosition == 1) {
            holder.seamlessText?.let {
                it.setTextIfChanged(description.orEmpty())
                it.isVisible = description != null
            }
            holder.seamlessIcon.isVisible = description == null
        }
    }

    private fun limitTextWidth(holder: MiuiMediaViewHolder, width: Int, height: Int) {
        val textEnd = width - processor.textEndInset(width, height)
        if (textEnd <= 0) return
        val offsetX = textOffsetX(holder)
        holder.titleText.clampWidth(textEnd, 1, offsetX)
        holder.artistText.clampWidth(textEnd, if (descriptionPosition == 2) 2 else 1, offsetX)
    }

    private fun textOffsetX(holder: MiuiMediaViewHolder): Float {
        val reclaimed =
            if (hideAlbumCover) (holder.titleText.left - holder.albumView.left).coerceAtLeast(0) else 0
        return textPaddingLeft - reclaimed
    }

    private fun applyLayoutTweaks(holder: MiuiMediaViewHolder) {
        // 清掉图片后这个 ImageView 仍会画出一层浅色底，压在标题下面
        if (hideAlbumCover && holder.albumView.alpha != 0f) holder.albumView.alpha = 0f

        val offsetX = textOffsetX(holder)
        holder.titleText.offset(offsetX, textPaddingTop)
        holder.artistText.offset(offsetX, textPaddingTop)

        if (actionSpacing == 100) return
        val gap = holder.action1.left - holder.action0.left
        if (gap <= 0) return
        val step = gap * (100 - actionSpacing) / 100f
        holder.action1.offset(-step, 0f)
        holder.action2.offset(-step * 2, 0f)
        holder.action3.offset(-step * 3, 0f)
        holder.action4.offset(-step * 4, 0f)
    }

    private fun View.offset(x: Float, y: Float) {
        if (translationX != x) translationX = x
        if (translationY != y) translationY = y
    }

    // 卡片可能在绑定之后才完成布局，那时 left 还都是 0，只有绘制前这一刻位置才可靠；
    // TransitionLayout 也会在每次状态切换时改写 alpha 与位置，只能在它之后再覆盖一遍
    private fun installLayoutTweaks(holder: MiuiMediaViewHolder, state: PanelState) {
        if (!hasLayoutTweaks) return
        if (state.shiftListener != null) {
            // 视图重新挂载后旧的 ViewTreeObserver 已失效，得重新注册
            if (holder.titleText.isAttachedToWindow) return
            state.shiftListener = null
        }
        val observer = holder.titleText.viewTreeObserver
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!holder.titleText.isAttachedToWindow) {
                    if (observer.isAlive) observer.removeOnPreDrawListener(this)
                    state.shiftListener = null
                    return true
                }
                applyLayoutTweaks(holder)
                return true
            }
        }
        state.shiftListener = listener
        observer.addOnPreDrawListener(listener)
    }

    private fun TextView.clampWidth(textEnd: Int, lines: Int, offsetX: Float) {
        val available = textEnd - left - offsetX.toInt()
        if (available <= 0) return
        if (maxWidth != available) maxWidth = available
        if (maxLines != lines) maxLines = lines
        if (ellipsize != TextUtils.TruncateAt.END) ellipsize = TextUtils.TruncateAt.END
    }

    private fun updateForegroundColors(holder: MiuiMediaViewHolder, colorConfig: MediaViewColorConfig) {
        val primaryColorStateList = ColorStateList.valueOf(colorConfig.textPrimary)
        val actionColorStateList = if (actionOpacity == 100) {
            primaryColorStateList
        } else {
            ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(colorConfig.textPrimary, actionOpacity * 255 / 100)
            )
        }
        holder.titleText.setTextColor(colorConfig.textPrimary)
        holder.artistText.setTextColor(colorConfig.textSecondary)
        if (emphasizeText) {
            bold(holder.titleText)
            bold(holder.artistText)
            bold(holder.elapsedTimeView)
            bold(holder.totalTimeView)
            // 时间文字直接压在封面上，只有它需要描边
            shadow(holder.elapsedTimeView, colorConfig.textPrimary)
            shadow(holder.totalTimeView, colorConfig.textPrimary)
        }
        holder.seamlessIcon.imageTintList = primaryColorStateList
        holder.action0.imageTintList = actionColorStateList
        holder.action1.imageTintList = actionColorStateList
        holder.action2.imageTintList = actionColorStateList
        holder.action3.imageTintList = actionColorStateList
        holder.action4.imageTintList = actionColorStateList
        holder.seekBar.thumb.setTintList(primaryColorStateList)
        holder.seekBar.progressTintList = primaryColorStateList
        holder.seekBar.progressBackgroundTintList = primaryColorStateList
        holder.elapsedTimeView.setTextColor(colorConfig.textPrimary)
        holder.totalTimeView.setTextColor(colorConfig.textPrimary)
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("UNCHECKED_CAST")
    private fun updateBackground(
        context: Context,
        state: PanelState,
        mediaData: Any,
        isArtWorkUpdate: Boolean,
        artwork: Icon?,
        pkgName: String,
        holder: MiuiMediaViewHolder
    ) {
        val artworkLayer = state.artworkLayer?.takeIf { artwork === state.artwork }
            ?: artwork?.loadDrawable(context)?.also { state.artworkLayer = it }
            ?: return
        if (useDisplayMetadata) {
            applyDisplayMetadata(context, state, mediaData, holder)
        }
        val reqId = state.nextBindRequestId++
        if (isArtWorkUpdate) {
            state.isArtworkBound = false
        }
        // Clip album cover image
//        val finalSize = min(artworkLayer.intrinsicWidth, artworkLayer.intrinsicHeight)
//        val bitmap = createBitmap(finalSize, finalSize)
//        val canvas = Canvas(bitmap)
//        val deltaW = (artworkLayer.intrinsicWidth - finalSize) / 2
//        val deltaH = (artworkLayer.intrinsicHeight - finalSize) / 2
//        artworkLayer.setBounds(-deltaW, -deltaH, finalSize + deltaW, finalSize + deltaH)
//        artworkLayer.draw(canvas)
//        val radius = 9.0f * context.resources.displayMetrics.density
//        val newBitmap = createBitmap(finalSize, finalSize)
//        val canvas1 = Canvas(newBitmap)
//        val paint = Paint()
//        val rect = Rect(0, 0, finalSize, finalSize)
//        val rectF = RectF(rect)
//        paint.isAntiAlias = true
//        canvas1.drawARGB(0, 0, 0, 0)
//        paint.color = Color.BLACK
//        canvas1.drawRoundRect(rectF, radius, radius, paint)
//        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
//        canvas1.drawBitmap(bitmap, rect, rect, paint)
//        if (!bitmap.isRecycled) {
//            bitmap.recycle()
//        }
        // Update album cover image
        if (hideAlbumCover) {
            // 摘掉封面会打散整张卡片的约束，只能留住占位再把文字挪进它的位置
            holder.albumView.setImageDrawable(null)
            holder.albumView.background = null
            holder.appIcon?.detach()
        } else {
            holder.albumView.setImageDrawable(artworkLayer)
        }
        if (hideSeamless && descriptionPosition != 1) {
            holder.seamless?.detach()
            holder.seamlessIcon.detach()
            holder.seamlessText?.detach()
        }
        // Capture width & height from views in foreground for artwork scaling in background
        val width: Int
        val height: Int
        if (holder.mediaBg.measuredWidth == 0 || holder.mediaBg.measuredHeight == 0) {
            if (lastWidth == 0 || lastHeight == 0) {
                width = artworkLayer.intrinsicWidth
                height = artworkLayer.intrinsicHeight
            } else {
                width = lastWidth
                height =
                    lastHeight
            }
        } else {
            width = holder.mediaBg.measuredWidth
            height = holder.mediaBg.measuredHeight
            lastWidth = width
            lastHeight = height
        }
        // Override colors set by the original method
        updateForegroundColors(holder, state.colorConfig)
        limitTextWidth(holder, width, height)
        installLayoutTweaks(holder, state)
        if (compactTimeLabels) {
            holder.elapsedTimeView.setPadding(0, 0, 0, 0)
            holder.totalTimeView.setPadding(0, 0, 0, 0)
        }

        // 播放状态每次变化都会重新绑定，封面没换时无需再跑一遍取色与模糊
        val boundBackground = state.artworkDrawable
        if (boundBackground != null && !isArtWorkUpdate && artwork === state.artwork) {
            if (holder.mediaBg.drawable !== boundBackground) {
                holder.mediaBg.setImageDrawable(boundBackground)
            }
            return
        }
        state.artwork = artwork

        GlobalScope.launch(Dispatchers.IO) {
            // Album art
            val mutableColorScheme: Any?
            val artworkDrawable: Drawable
            val isArtworkBound: Boolean
            val wallpaperColors = context.getWallpaperColor(artwork)
            if (wallpaperColors != null) {
                val tempColorScheme = try {
                    conColorScheme3.newInstance(wallpaperColors, true, enumStyleContent)
                } catch (_: IllegalArgumentException) {
                    conColorScheme2.newInstance(wallpaperColors, enumStyleContent)
                }
                mutableColorScheme = tempColorScheme
                artworkDrawable = context.getScaledBackground(artwork, height, height) ?: Color.TRANSPARENT.toDrawable()
                isArtworkBound = true
            } else {
                // If there's no artwork, use colors from the app icon
                artworkDrawable = Color.TRANSPARENT.toDrawable()
                isArtworkBound = false
                try {
                    val icon = context.packageManager.getApplicationIcon(pkgName)
                    val tempColorScheme = try {
                        conColorScheme3.newInstance(WallpaperColors.fromDrawable(icon), true, enumStyleContent)
                    } catch (_: IllegalArgumentException) {
                        conColorScheme2.newInstance(wallpaperColors, enumStyleContent)
                    }
                    mutableColorScheme = tempColorScheme ?: throw Exception()
                } catch (_: Exception) {
                    logW(TAG, lpparam.packageName, "updateBackground(method) application not found!")
                    return@launch
                }
            }
            var colorConfig = defaultColorConfig
            if (mutableColorScheme != null) {
                val neutral1 = fldTonalPaletteAllShades?.get(fldColorSchemeNeutral1!!.get(mutableColorScheme)) as? List<Int>
                val neutral2 = fldTonalPaletteAllShades?.get(fldColorSchemeNeutral2!!.get(mutableColorScheme)) as? List<Int>
                val accent1 = fldTonalPaletteAllShades?.get(fldColorSchemeAccent1!!.get(mutableColorScheme)) as? List<Int>
                val accent2 = fldTonalPaletteAllShades?.get(fldColorSchemeAccent2!!.get(mutableColorScheme)) as? List<Int>
                if (neutral1 != null && neutral2 != null && accent1 != null && accent2 != null) {
                    colorConfig = processor.convertToColorConfig(artworkDrawable, neutral1, neutral2, accent1, accent2)
                }
            }
            val processedArtwork =
                processor.processAlbumCover(
                    artworkDrawable,
                    colorConfig,
                    context,
                    width,
                    height
                )
            holder.mediaBg.post(Runnable {
                if (reqId < state.boundId) {
                    return@Runnable
                }
                state.boundId = reqId
                if (state.colorConfig != colorConfig) {
                    updateForegroundColors(holder, colorConfig)
                    state.colorConfig = colorConfig
                }

                val isNewSession = state.pkgName != pkgName
                state.pkgName = pkgName
                var background = state.artworkDrawable
                if (background == null || isNewSession) {
                    background = processor.createBackground(processedArtwork, colorConfig)
                    state.artworkDrawable = background
                } else if (isArtWorkUpdate || (!state.isArtworkBound && isArtworkBound)) {
                    background.updateAlbumCover(processedArtwork, colorConfig)
                }
                state.isArtworkBound = isArtworkBound

                background.setBounds(0, 0, width, height)
                holder.mediaBg.setPadding(0, 0, 0, 0)
                if (holder.mediaBg.drawable !== background) {
                    holder.mediaBg.setImageDrawable(background)
                }
            })
        }
    }

    private const val BOLD_WEIGHT = "'wght' 500"
    private const val TITLE_SEPARATOR = " · "
    private const val DESCRIPTION_SCALE = 0.8f
    private const val SHADOW_DARK = 0x66000000
    private const val SHADOW_LIGHT = 0x66FFFFFF.toInt()

    private class PanelState {
        var boundId = 0
        var nextBindRequestId = 0
        var artwork: Icon? = null
        var shiftListener: ViewTreeObserver.OnPreDrawListener? = null
        var artworkLayer: Drawable? = null
        var controller: MediaController? = null
        var artworkDrawable: MediaControlBgDrawable? = null
        var isArtworkBound = false
        var pkgName = ""
        var colorConfig = defaultColorConfig
    }

    data class MiuiMediaViewHolder(
        var innerHashCode: Int,
        var titleText: TextView,
        var artistText: TextView,
        var albumView: ImageView,
        var mediaBg: ImageView,
        var seamlessIcon: ImageView,
        var seamlessText: TextView?,
        var seamless: View?,
        var appIcon: ImageView?,
        var action0: ImageButton,
        var action1: ImageButton,
        var action2: ImageButton,
        var action3: ImageButton,
        var action4: ImageButton,
        var elapsedTimeView: TextView,
        var totalTimeView: TextView,
        var seekBar: SeekBar
    )

}
