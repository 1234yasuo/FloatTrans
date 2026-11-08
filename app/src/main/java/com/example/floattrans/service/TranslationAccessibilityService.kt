package com.example.floattrans.service
import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

data class TextBlock(val text: String, val rect: Rect)

class TranslationAccessibilityService : AccessibilityService() {
    companion object { 
        @Volatile var instance: TranslationAccessibilityService? = null
            private set 
    }

    override fun onServiceConnected() { 
        super.onServiceConnected()
        instance = this 
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    
    override fun onDestroy() { 
        instance = null
        super.onDestroy() 
    }
    
    // 默认全量提取（供用户点击翻译时使用）
    fun extractTextBlocks(): List<TextBlock> {
        return scanNodes(isQuickScan = false)
    }

    // 快速提取（供后台1秒检测语言使用，最多30块，极大降低压力）
    fun extractTextBlocksQuick(): List<TextBlock> {
        return scanNodes(isQuickScan = true)
    }

    private fun scanNodes(isQuickScan: Boolean): List<TextBlock> {
        val root = rootInActiveWindow ?: return emptyList()
        val blocks = mutableListOf<TextBlock>()
        traverse(root, blocks, 0, isQuickScan)
        return blocks
    }
    
    private fun traverse(node: AccessibilityNodeInfo?, blocks: MutableList<TextBlock>, depth: Int, isQuickScan: Boolean) {
        if (node == null || depth > 50) return
        // 如果是快速扫描，收集满 30 块就直接停止，不再耗费 CPU 遍历
        if (isQuickScan && blocks.size >= 30) return

        var text = node.text?.toString()?.trim() ?: node.contentDescription?.toString()?.trim()
        if (text != null && text.length > 2000) {
            text = text.substring(0, 2000)
        }
        
        if (!text.isNullOrEmpty() && text.length > 1) {
            try {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() > 50 && rect.height() > 20 && rect.top < resources.displayMetrics.heightPixels && rect.bottom > 0) {
                    blocks.add(TextBlock(text, rect))
                }
            } catch (e: Exception) {
                // 防御性捕获
            }
        }
        for (i in 0 until node.childCount) {
            if (isQuickScan && blocks.size >= 30) break 
            traverse(node.getChild(i), blocks, depth + 1, isQuickScan)
        }
    }
}
