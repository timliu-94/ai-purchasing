package com.aipurchase.app

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @Test fun clickAndRecreateKeepsCount() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withText("已点击 0 次")).check(matches(isDisplayed()))
            onView(withText(R.string.increment)).perform(click())
            onView(withText("已点击 1 次")).check(matches(isDisplayed()))
            scenario.recreate()
            onView(withText("已点击 1 次")).check(matches(isDisplayed()))
        }
    }
}
