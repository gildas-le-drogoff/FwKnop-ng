package fr.gildasledrogoff.fwknop
import android.app.KeyguardManager
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assume.assumeTrue
object DeviceCredential {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    val device: UiDevice get() = UiDevice.getInstance(instrumentation)
    val pin: String? get() = InstrumentationRegistry.getArguments().getString("devicePin")
    private val keyguard: KeyguardManager
        get() = instrumentation.targetContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    fun assumeSecureWithPin(): String {
        assumeTrue("device has no secure lock screen", keyguard.isDeviceSecure)
        return requireNotNull(pin.also { assumeTrue("no devicePin argument", it != null) })
    }
    fun unlockLockScreen() {
        val pin = assumeSecureWithPin()
        device.sleep()
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        Thread.sleep(UI_SETTLE_MS)
        typePin(pin)
        waitUntil { !keyguard.isKeyguardLocked }
    }
    fun typePin(pin: String) {
        device.executeShellCommand("input text $pin")
        device.executeShellCommand("input keyevent KEYCODE_ENTER")
    }
    fun waitUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "condition not met in $timeoutMs ms" }
            Thread.sleep(POLL_MS)
        }
    }
    private const val UI_SETTLE_MS = 1_000L
    private const val POLL_MS = 100L
}
