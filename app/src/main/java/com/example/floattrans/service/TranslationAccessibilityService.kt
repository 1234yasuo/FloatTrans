package com.example.floattrans.service
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
class TranslationAccessibilityService : AccessibilityService() {
    companion object { @Volatile var instance: TranslationAccessibilityService? = null; private set }
    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { instance = null; super.onDestroy() }
    fun extractVisibleText(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder(); val seen = HashSet<String>(); traverse(root, sb, seen, 0); return sb.toString().trim()
    }
    private fun traverse(node: AccessibilityNodeInfo?, sb: StringBuilder, seen: MutableSet<String>, depth: Int) {
        if (node == null || depth > 40) return
        node.text?.toString()?.trim()?.let { if (it.isNotEmpty() && it.length < 2000 && seen.add(it)) sb.append(it).append('\n') }
        node.contentDescription?.toString()?.trim()?.let { if (it.isNotEmpty() && it.length < 500 && seen.add(it)) sb.append(it).append('\n') }
        node.hintText?.toString()?.trim()?.let { if (it.isNotEmpty() && it.length < 500 && seen.add(it)) sb.append(it).append('\n') }
        node.paneTitle?.toString()?.trim()?.let { if (it.isNotEmpty() && it.length < 500 && seen.add(it)) sb.append(it).append('\n') }
        for (i in 0 until node.childCount) traverse(node.getChild(i), sb, seen, depth + 1)
    }
}
