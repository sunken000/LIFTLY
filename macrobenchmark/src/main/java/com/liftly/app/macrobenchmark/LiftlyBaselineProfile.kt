package com.liftly.app.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiftlyBaselineProfile {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun criticalTrainingJourney() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
    ) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.textContains("LIFTLY")), 3_000)

        listOf("Treinos", "Progresso", "Hoje").forEach { label ->
            device.findObject(By.text(label))?.click()
            device.waitForIdle()
        }

        device.findObject(By.text("Iniciar treino"))?.click()
        device.wait(Until.hasObject(By.textContains("SÉRIE")), 2_500)
        device.findObject(By.text("Concluir série"))?.click()
        device.findObject(By.textContains("Finalizar"))?.click()
    }

    private companion object {
        const val PACKAGE_NAME = "com.liftly.app"
    }
}