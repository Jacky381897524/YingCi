package cn.yingci.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class UiRevisionTests {
    @get:Rule val compose=createComposeRule()
    @Test fun newSelectorClickAndSwipe(){
        compose.setContent{var selected by remember{mutableIntStateOf(0)};MaterialTheme{GlassSelector(listOf("修改版","原始版"),selected,false,true,Modifier.width(340.dp).height(44.dp)){selected=it}}}
        compose.onNodeWithText("原始版").performClick().assertIsSelected()
        compose.onRoot().performTouchInput{swipeLeft()}
        compose.onNodeWithText("修改版").assertIsSelected()
    }
    @Test fun themeCardsAndGlobalWeightCompose(){
        compose.setContent{var selected by remember{mutableIntStateOf(0)};CompositionLocalProvider(LocalPrefs provides Preferences(fontWeight=700)){MaterialTheme{Column(Modifier.width(280.dp)){ThemeCards(selected){selected=it};AppText("全局粗体")}}}}
        compose.onNodeWithText("深色").performClick();compose.onNodeWithContentDescription("已选择").assertExists();compose.onNodeWithText("全局粗体").assertIsDisplayed()
    }
    @Test fun oldSettingsMigrateAndNewWeightsPersist()=runBlocking{
        val context:Context=ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("preferences",Context.MODE_PRIVATE).edit().clear().putInt("fontPercent",120).putInt("theme",2).putBoolean("followFont",false).commit()
        val repo=Repository(context);val old=repo.preferences()
        assertEquals(115,old.fontPercent);assertEquals(400,old.fontWeight);assertEquals(2,old.theme);assertFalse(old.followFont)
        repo.savePreferences(old.copy(fontPercent=160,fontWeight=700))
        val restored=Repository(context).preferences();assertEquals(700,restored.fontWeight);assertEquals(145,restored.fontPercent);assertEquals(2,restored.theme)
    }
    @Test fun displayDraftDoesNotPersistUntilSave(){
        val app:android.app.Application=ApplicationProvider.getApplicationContext()
        val vm=AppViewModel(app)
        compose.setContent{val prefs by vm.preferences.collectAsState();CompositionLocalProvider(LocalPrefs provides prefs){MaterialTheme{DisplaySettings(vm){}}}}
        compose.onNodeWithText("折射强度").assertIsDisplayed()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo))[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress){it(90f)}
        compose.runOnIdle{assertEquals(84f,vm.preferences.value.display["refraction"],0f)}
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(5000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();vm.preferences.value.display["refraction"]==90f}
        assertEquals(90f,Repository(app).preferences().display["refraction"],0f)
    }
}
