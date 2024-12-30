package de.nrunodos.plugins

import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ConvertPropertiesToYamlActionTest : BasePlatformTestCase() {

  fun testConvertPropertiesToYaml() {
    val propertiesFile = myFixture.addFileToProject(
      "test.properties", """
            key1=value1
            # real important comment
            key2=value2
            nested.group.key1=nestedValue
            application.servers[0].ip=127.0.0.1
            application.servers[0].path=/path1
            application.servers[1].ip=127.0.0.2
            
            # explanation of the weird path
            application.servers[1].path=/path2: must be escaped
            nested.group.key2=line\
              Bre\
              ak
            application.servers[2].ip=127.0.0.3
            application.servers[2].path=/path3\
              anotherObscurePath
   """.trimIndent(),
    )

    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.VIRTUAL_FILE, propertiesFile.virtualFile)
      .build()

    val action = ConvertPropertiesToYamlAction()
    val e = AnActionEvent.createEvent(action, dataContext, null, "", ActionUiKind.TOOLBAR, null)
    action.actionPerformed(e)

    val yamlFile = propertiesFile.parent?.findFile("test.yml")
    assertNotNull("YAML file should be created", yamlFile)

    val yamlContent = yamlFile?.virtualFile?.contentsToByteArray()?.toString(Charsets.UTF_8)
    assertEquals(
      """
            key1: value1
            # real important comment
            key2: value2
            nested:
              group:
                key1: nestedValue
                key2: "line\nBre\nak"
            application:
              servers:
                - ip: 127.0.0.1
                  path: /path1
                - ip: 127.0.0.2
                  # explanation of the weird path
                  path: '/path2: must be escaped'
                - ip: 127.0.0.3
                  path: "/path3\nanotherObscurePath"
        """.trimIndent(), yamlContent?.trim()
    )
  }
}
