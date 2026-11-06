package com.lessoften.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.Bitmap
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.*

/** Blocks ordinary clipboard, drag/drop, autofill, rich content and context-menu insertion. */
class TypedQuoteInput(context: Context) : EditText(context) {
    init {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        isLongClickable = false
        setTextIsSelectable(false)
        importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        importantForContentCapture = IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
        val noMenu = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) = false
            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false
            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false
            override fun onDestroyActionMode(mode: ActionMode?) {}
        }
        customSelectionActionModeCallback = noMenu
        customInsertionActionModeCallback = noMenu
    }
    override fun onTextContextMenuItem(id: Int) = false
    override fun onReceiveContent(payload: ContentInfo): ContentInfo = payload
    override fun onDragEvent(event: DragEvent?) = false
    override fun onKeyShortcut(keyCode: Int, event: KeyEvent?): Boolean =
        if (keyCode == KeyEvent.KEYCODE_V || keyCode == KeyEvent.KEYCODE_INSERT) true else super.onKeyShortcut(keyCode, event)
    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean =
        if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_PASTE ||
            action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT) false else super.performAccessibilityAction(action, arguments)
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        outAttrs.imeOptions = outAttrs.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        val connection = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(connection, false) {
            override fun performContextMenuAction(id: Int) = false
            override fun commitContent(inputContentInfo: android.view.inputmethod.InputContentInfo, flags: Int, opts: Bundle?) = false
        }
    }
}
class GateView(context: Context, label: String, val quote: Quote, sessionsToday: Int?, complete: (String) -> Unit, cancel: () -> Unit) : ScrollView(context) {
    val input = TypedQuoteInput(context)
    private val appIcon = ImageView(context)
    private val error: TextView
    init {
        setBackgroundColor(Color.rgb(23, 25, 23))
        isFillViewport = true
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28.dp, 32.dp, 28.dp, 24.dp)
        }
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        fun text(value: String, size: Float) = TextView(context).apply {
            text = value; textSize = size; setTextColor(Color.rgb(230, 232, 223)); setPadding(0, 12.dp, 0, 12.dp)
        }
        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(appIcon, LinearLayout.LayoutParams(36.dp, 36.dp).apply { marginEnd = 14.dp })
        header.addView(text(label, 26f), LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        column.addView(header)
        column.addView(text("10-minute session", 13f).apply { setTextColor(Color.rgb(167, 175, 161)) })
        if (sessionsToday != null) column.addView(text("$sessionsToday ${if (sessionsToday == 1) "session" else "sessions"} opened today", 13f).apply { setTextColor(Color.rgb(167, 175, 161)) })
        column.addView(text("Type this quote to open for 10 minutes.", 16f))
        column.addView(text(quote.text, 23f))
        column.addView(text(quote.attribution, 13f).apply { setTextColor(Color.rgb(167, 175, 161)) })
        input.apply {
            hint = "Type the quote"; setTextColor(Color.rgb(230, 232, 223)); setHintTextColor(Color.rgb(167, 175, 161)); minLines = 3; textSize = 20f
            fun boundary(stroke: Int) = GradientDrawable().apply { setColor(Color.rgb(35, 42, 34)); cornerRadius = 14.dp.toFloat(); setStroke(2.dp, stroke) }
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), boundary(Color.rgb(166, 183, 154)))
                addState(intArrayOf(), boundary(Color.rgb(55, 62, 52)))
            }
            setPadding(16.dp, 12.dp, 16.dp, 12.dp)
        }
        column.addView(input, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        error = text("", 15f); column.addView(error)
        fun button(label: String, prominent: Boolean, action: () -> Unit) = Button(context).apply {
            text = label; isAllCaps = false; minHeight = 48.dp
            setTextColor(if (prominent) Color.rgb(23, 25, 23) else Color.rgb(230, 232, 223))
            backgroundTintList = null
            fun shape(fill: Int) = GradientDrawable().apply { setColor(fill); cornerRadius = 12.dp.toFloat() }
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), shape(Color.rgb(125, 143, 116)))
                addState(intArrayOf(android.R.attr.state_focused), shape(Color.rgb(125, 143, 116)))
                addState(intArrayOf(), shape(if (prominent) Color.rgb(166, 183, 154) else Color.rgb(35, 42, 34)))
            }
            setOnClickListener { action() }
        }
        column.addView(button("Open for 10 minutes", true) { complete(input.text.toString()) }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10.dp })
        column.addView(button("Go Home", false) { cancel() }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        isFocusableInTouchMode = true
    }
    fun showError(message: String) { error.text = message }
    fun setAppIcon(bitmap: Bitmap?) { if (bitmap != null) appIcon.setImageBitmap(bitmap) }
    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
}
