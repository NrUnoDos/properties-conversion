package de.nrunodos.plugins

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.Messages

class ConvertPropertiesToYamlAction : AnAction() {

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file != null && "properties" == file.extension
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        ApplicationManager.getApplication().runWriteAction {
            try {
                val propertiesContent = String(file.contentsToByteArray())
                val yamlContent = convertPropertiesToYaml(propertiesContent)

                val yamlFile = file.parent.createChildData(this, "${file.nameWithoutExtension}.yml")
                yamlFile.setBinaryContent(yamlContent.toByteArray())

                FileEditorManager.getInstance(project).openFile(yamlFile, true)
            } catch (ex: Exception) {
                Messages.showErrorDialog(project, "Error converting file: ${ex.message}", "Conversion Error")
            }
        }
    }

    private fun convertPropertiesToYaml(propertiesContent: String): String {
        val yamlMap = mutableMapOf<String, Any>()
        val comments = mutableMapOf<String, MutableList<String>>()
        var lastKey = ""

        propertiesContent.lines().forEach { line ->
            when {
                line.trim().isEmpty() -> { /* skip empty line */ }
                line.trim().startsWith("#") -> {
                    comments.getOrPut(lastKey) { mutableListOf() }.add(line.trim())
                }
                "=" in line -> {
                    val (key, value) = line.split("=", limit = 2)
                    lastKey = key.trim()
                    addToYamlMap(yamlMap, lastKey, value.trim())
                }
                else -> addToYamlMap(yamlMap, lastKey, "\\n" + line.trim())
            }
        }

        return generateYaml(yamlMap, comments)
    }

    private fun addToYamlMap(map: MutableMap<String, Any>, key: String, value: String) {
        val keys = key.split(".")
        var current: Any = map

        keys.dropLast(1).forEach { k ->
            if (k.contains("[") && k.contains("]")) {
                val (arrayKey, indexStr) = k.split("[", "]")
                val index = indexStr.toInt()

                current = when (current) {
                    is MutableMap<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        (current as MutableMap<String, Any>).getOrPut(arrayKey) { mutableListOf<MutableMap<String, Any>>() }
                    }
                    else -> current
                }

                if (current is MutableList<*>) {
                    @Suppress("UNCHECKED_CAST")
                    val list = current as MutableList<MutableMap<String, Any>>
                    while (list.size <= index) {
                        list.add(mutableMapOf())
                    }
                    current = list[index]
                }
            } else {
                current = when (current) {
                    is MutableMap<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        (current as MutableMap<String, Any>).getOrPut(k) { mutableMapOf<String, Any>() }
                    }
                    else -> current
                }
            }
        }

        val lastKey = keys.last()
        if (lastKey.contains("[") && lastKey.contains("]")) {
            val (arrayKey, indexStr) = lastKey.split("[", "]")
            val index = indexStr.toInt()

            if (current is MutableMap<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val list = (current as MutableMap<String, Any>).getOrPut(arrayKey) { mutableListOf<MutableMap<String, Any>>() } as MutableList<MutableMap<String, Any>>
                while (list.size <= index) {
                    list.add(mutableMapOf())
                }
                var existingValue = unwrap((list[index] as MutableMap<String, String>).getOrDefault(arrayKey, ""))
                if (existingValue.endsWith("\\") && value.startsWith("\n")) {
                    existingValue = existingValue.dropLast(1)
                }
                list[index][arrayKey] = formatValue(existingValue + value)
            }
        } else {
            if (current is MutableMap<*, *>) {
                var existingValue = unwrap((current as MutableMap<String, String>).getOrDefault(lastKey, ""))
                if (existingValue.endsWith("\\") && value.startsWith("\\n")) {
                    existingValue = existingValue.dropLast(1)
                }
                @Suppress("UNCHECKED_CAST")
                (current as MutableMap<String, Any>)[lastKey] = formatValue(existingValue + value)
            }
        }
    }

    private fun generateYaml(map: Map<String, Any>, comments: Map<String, List<String>>, indent: String = "", fullKey: String = ""): String {
        val sb = StringBuilder()

        map.forEach { (key, value) ->
            val currentKey = if (fullKey.isEmpty()) key else "$fullKey.$key"

            when (value) {
                is Map<*, *> -> {
                    sb.appendLine("$indent$key:")
                    sb.append(generateYaml(value as Map<String, Any>, comments, "$indent  ", currentKey))
                }

                is List<*> -> {
                    sb.appendLine("$indent$key:")
                    value.forEachIndexed { index, item ->
                        if (item is Map<*, *>) {
                            sb.append("$indent  - ")
                            sb.append(
                                generateYaml(
                                    item as Map<String, Any>,
                                    comments,
                                    "$indent    ",
                                    "$currentKey[$index]"
                                ).trimStart()
                            )
                        } else {
                            sb.appendLine("$indent  - $item")
                        }
                    }
                }

                else -> sb.appendLine("$indent$key: $value")
            }

            comments[currentKey]?.forEach { comment ->
                sb.appendLine("$indent$comment")
            }
        }

        return sb.toString()
    }

    private fun unwrap(value: String): String {
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.dropLast(1).drop(1)
        }
        if (value.startsWith("'") && value.endsWith("'")) {
            return value.dropLast(1).drop(1)
        }
        return value
    }

    private fun formatValue(value: String): Any {
        return when {
            value.contains("\\n") -> "\"" + value + "\""
            value.contains(":") -> "'$value'"
            else -> value.trim()
        }
    }
}