package com.example.floattrans.service
import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
data class TextBlock(val text: String, val rect: Rect)
class TranslationAccessibilityService : AccessibilityService() {
    companion object { @Volatile var instance: TranslationAccessibilityService? = null; private set }
    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { instance = null; super.onDestroy() }
    fun extractTextBlocks(): List<TextBlock> {
        val root = rootInActiveWindow ?: return emptyList()
        val blocks = mutableListOf<TextBlock>()
        traverse(root, blocks, 0)
        return blocks
    }
    private fun traverse(node: AccessibilityNodeInfo?, blocks: MutableList<TextBlock>, depth: Int) {
        if (node == null || depth > 40) return
        val text = node.text?.toString()?.trim() ?: node.contentDescription?.toString()?.trim()
        if (!text.isNullOrEmpty() && text.length > 3) {
            val rect = Rect(); node.getBoundsInScreen(rect)
            if (rect.width() > 100 && rect.height() > 30) blocks.add(TextBlock(text, rect))
        }
        for (i in 0 until node.childCount) traverse(node.getChild(i), blocks, depth + 1)
    }
}
