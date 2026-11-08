package com.example.floattrans.service
import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

data class TextBlock(val text: String, val rect: Rect)

class TranslationAccessibilityService : AccessibilityService() {
    companion object { @Volatile var instance: TranslationAccessibilityService? = null; private set }
    var onContentChanged: (() -> Unit)? = null
    
    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            onContentChanged?.invoke()
        }
    }
    override fun onInterrupt() {}
    override fun onDestroy() { instance = null; onContentChanged = null; super.onDestroy() }
    
    // 提取文本块，保留换行和段落结构
    fun extractTextBlocks(): List<TextBlock> {
        val root = rootInActiveWindow ?: return emptyList()
        val blocks = mutableListOf<TextBlock>()
        traverse(root, blocks, 0)
        return blocks
    }
    
    private fun traverse(node: AccessibilityNodeInfo?, blocks: MutableList<TextBlock>, depth: Int) {
        if (node == null || depth > 50) return
        val text = node.text?.toString()?.trim() ?: node.contentDescription?.toString()?.trim()
        if (!text.isNullOrEmpty() && text.length > 1) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            // 只提取在屏幕内的内容
            if (rect.width() > 50 && rect.height() > 20 && rect.top < resources.displayMetrics.heightPixels && rect.bottom > 0) {
                blocks.add(TextBlock(text, rect))
            }
        }
        for (i in 0 until node.childCount) traverse(node.getChild(i), blocks, depth + 1)
    }
}
