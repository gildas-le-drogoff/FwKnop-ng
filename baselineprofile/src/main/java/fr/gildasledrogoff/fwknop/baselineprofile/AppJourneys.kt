package fr.gildasledrogoff.fwknop.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val TARGET_PACKAGE = "fr.gildasledrogoff.fwknop"

// Compose test tags exposed as resource ids by the config list (testTagsAsResourceId).
private const val CONFIG_LIST = "config_list"
private const val CONFIG_GRID = "config_grid"
private const val WAIT_MS = 10_000L

/**
 * Waits until the config list is drawn. The help screen shown on first launch is dismissed. The app
 * lock must be off: a locked vault keeps the app on its lock screen.
 */
fun MacrobenchmarkScope.awaitConfigList() {
    if (!device.wait(Until.hasObject(By.res(CONFIG_LIST)), WAIT_MS)) {
        // First launch: the help screen is pushed above the list.
        device.pressBack()
        check(device.wait(Until.hasObject(By.res(CONFIG_LIST)), WAIT_MS)) {
            "Config list not shown: disable the app lock on the benchmark device"
        }
    }
}

/** Scrolls the config grid back and forth so card composition and drawing are profiled. */
fun MacrobenchmarkScope.scrollConfigs() {
    val grid = device.findObject(By.res(CONFIG_GRID)) ?: return
    grid.setGestureMargin(device.displayWidth / 5)
    grid.fling(Direction.DOWN)
    device.waitForIdle()
    grid.fling(Direction.UP)
    device.waitForIdle()
}
