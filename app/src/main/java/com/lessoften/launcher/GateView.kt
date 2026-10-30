package com.lessoften.launcher

import android.content.Context
import android.graphics.Color
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
class GateView(context: Context, label: String, val quote: Quote, complete: (String) -> Unit, cancel: () -> Unit) : ScrollView(context) {
    val input = TypedQuoteInput(context)
    private val error: TextView
    init {
        setBackgroundColor(Color.rgb(16, 16, 16))
        isFillViewport = true
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28.dp, 32.dp, 28.dp, 24.dp)
        }
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        fun text(value: String, size: Float) = TextView(context).apply {
            text = value; textSize = size; setTextColor(Color.LTGRAY); setPadding(0, 12.dp, 0, 12.dp)
        }
        column.addView(text(label, 26f))
        column.addView(text("Type this quote to open for 10 minutes.", 16f))
        column.addView(text(quote.text, 23f))
        column.addView(text(quote.attribution, 13f))
        input.apply { hint = "Type the quote"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); minLines = 3; textSize = 20f }
        column.addView(input, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        error = text("", 15f); column.addView(error)
        column.addView(Button(context).apply { text = "Open for 10 minutes"; setOnClickListener { complete(input.text.toString()) } })
        column.addView(Button(context).apply { text = "Cancel and go Home"; setOnClickListener { cancel() } })
        setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        isFocusableInTouchMode = true
    }
    fun showError(message: String) { error.text = message }
    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
}
