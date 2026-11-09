package uk.co.traynor.privategallery.launcher

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherSmartFolderStore73Test {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val personalApp = "com.example.chat/.ChatActivity"
    private val workApp = "$personalApp#42"

    @Test fun smartFoldersAndProfileKeysSurviveLocalAndPortableLayouts() {
        val store = NovaLauncherStore(context)
        val before = store.read()
        try {
            val smartFolder = LauncherItem.SmartFolder(
                LauncherCategory.SUGGESTIONS, "My suggestions", listOf(workApp, personalApp),
            )
            val state = NovaLauncherState(
                configured = true,
                cover = LauncherLayout(
                    pages = listOf(listOf(LauncherItem.App(workApp)), listOf(smartFolder)),
                    dock = listOf(LauncherItem.Folder("Chat", listOf(workApp, personalApp))),
                    favourites = setOf(workApp, personalApp),
                ),
                launchCounts = mapOf(workApp to 2, personalApp to 1),
            )
            assertTrue(store.save(state))
            assertEquals(state, NovaLauncherStore(context).read())

            val backup = store.exportLayout()
            assertTrue(store.save(state.copy(cover = LauncherLayout())))
            assertTrue(store.restoreLayout(backup))
            assertEquals(state, store.read())
        } finally {
            assertTrue(store.save(before))
        }
    }

    @Test fun smartFolderImportRejectsNonAppKeysWithoutReplacingExistingLayout() {
        val store = NovaLauncherStore(context)
        val before = store.read()
        try {
            val folder = LauncherItem.SmartFolder(LauncherCategory.RECENTLY_USED)
            val state = NovaLauncherState(cover = LauncherLayout(pages = listOf(listOf(folder))))
            assertTrue(store.save(state))
            val exported = JSONObject(store.exportLayout().decodeToString())
            assertEquals(1, exported.getInt("schema"))
            val item = exported.getJSONObject("cover").getJSONArray("pages")
                .getJSONArray(0).getJSONObject(0)
            item.getJSONArray("included").put("intent://unsafe")

            assertFalse(store.restoreLayout(exported.toString().encodeToByteArray()))
            assertEquals(state, store.read())
        } finally {
            assertTrue(store.save(before))
        }
    }

    @Test fun legacyPortableLayoutRetainsPlainShortcutsAndManualFolders() {
        val store = NovaLauncherStore(context)
        val before = store.read()
        try {
            val legacy = """
                {
                  "type":"nova-launcher-layout","schema":1,"separateDisplays":true,
                  "cover":{
                    "pages":[[{"type":"app","component":"$personalApp"},
                              {"type":"folder","name":"Chat","apps":["$personalApp"]}]],
                    "dock":[{"type":"workspace"}],"favourites":["$personalApp"]
                  },
                  "inner":{"pages":[[]],"dock":[{"type":"workspace"}],"favourites":[]},
                  "appearance":{"wallpaper":"OCEAN","theme":"SYSTEM","accent":"SKY","iconShape":"ROUNDED",
                                "iconSize":"MEDIUM","labels":true,"grid":"STANDARD","dock":"GLASS"},
                  "drawer":{"orientation":"VERTICAL","search":"DRAWER","sort":"NAME",
                            "folders":true,"frequent":true,"shortcutSearch":false},
                  "gesture":"NONE","hideBlocked":false
                }
            """.trimIndent().encodeToByteArray()
            assertTrue(store.restoreLayout(legacy))
            assertEquals(
                LauncherLayout(
                    pages = listOf(listOf(LauncherItem.App(personalApp), LauncherItem.Folder("Chat", listOf(personalApp)))),
                    favourites = setOf(personalApp),
                ),
                store.read().cover,
            )
        } finally {
            assertTrue(store.save(before))
        }
    }
}
