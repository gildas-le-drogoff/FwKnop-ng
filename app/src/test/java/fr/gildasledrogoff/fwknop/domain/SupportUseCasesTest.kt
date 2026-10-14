package fr.gildasledrogoff.fwknop.domain
import fr.gildasledrogoff.fwknop.domain.error.ConfigError
import fr.gildasledrogoff.fwknop.domain.error.SettingsError
import fr.gildasledrogoff.fwknop.domain.model.AccessRule
import fr.gildasledrogoff.fwknop.domain.model.AppSettings
import fr.gildasledrogoff.fwknop.domain.model.ConfigDraft
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.KnockOutcome
import fr.gildasledrogoff.fwknop.domain.model.KnockRequest
import fr.gildasledrogoff.fwknop.domain.model.KnockTimerOutcome
import fr.gildasledrogoff.fwknop.domain.model.KnockTimerPlan
import fr.gildasledrogoff.fwknop.domain.model.Nickname
import fr.gildasledrogoff.fwknop.domain.model.PortAccess
import fr.gildasledrogoff.fwknop.domain.model.QrKeys
import fr.gildasledrogoff.fwknop.domain.model.ResolvedSourceIp
import fr.gildasledrogoff.fwknop.domain.model.SecretKey
import fr.gildasledrogoff.fwknop.domain.model.SourceIpMethod
import fr.gildasledrogoff.fwknop.domain.model.toDraft
import fr.gildasledrogoff.fwknop.domain.usecase.OtherNfcTagDigestsUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ParseQrClientIpUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ParseQrConfigUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.SaveConfigUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.SaveSettingsUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.GetExternalAppDelayUseCase
import fr.gildasledrogoff.fwknop.data.local.preferences.SettingsPreferences
import fr.gildasledrogoff.fwknop.data.local.preferences.toAppSettings
import fr.gildasledrogoff.fwknop.data.local.preferences.write
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlin.time.Duration.Companion.seconds
import fr.gildasledrogoff.fwknop.domain.usecase.ScheduleKnockTimerUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ValidateConfigUseCase
import fr.gildasledrogoff.fwknop.fakes.FakeConfigRepository
import fr.gildasledrogoff.fwknop.fakes.FakeKnockTimerScheduler
import fr.gildasledrogoff.fwknop.fakes.FakeNfcTagRepository
import fr.gildasledrogoff.fwknop.fakes.FakeSettingsRepository
import fr.gildasledrogoff.fwknop.fakes.sampleConfig
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
class SupportUseCasesTest {
    @Test
    fun `adding a config to a tag drops only its previous digests`() = runTest {
        val tags = FakeNfcTagRepository()
        val home = tags.digest(Nickname("home"))
        val office = tags.digest(Nickname("office"))
        val stale = tags.digest(Nickname("home"))
        val others = OtherNfcTagDigestsUseCase(tags)(listOf(home, office, stale, office), Nickname("home"))
        assertThat(others).containsExactly(office)
    }
    @Test
    fun `qr keys prefer base64 stanzas`() {
        val qr = ParseQrConfigUseCase()("KEY_BASE64:a2V5 HMAC_KEY:plain")
        assertThat(qr.keys).isEqualTo(QrKeys(SecretKey("a2V5", true), SecretKey("plain", false)))
        assertThat(qr.config).isNull()
    }
    @Test
    fun `qr client ip is extracted`() {
        assertThat(ParseQrClientIpUseCase()("FOO:bar CLIENT_IP:198.51.100.4")).isEqualTo("198.51.100.4")
        assertThat(ParseQrClientIpUseCase()("garbage")).isNull()
    }
    @Test
    fun `settings url must be https with a valid host`() = runTest {
        val repository = FakeSettingsRepository()
        val save = SaveSettingsUseCase(repository)
        val bad = save(AppSettings(ipLookupUrl = "ftp://api.ipify.org"))
        assertThat(bad).isEqualTo(DomainResult.Failure(SettingsError.InvalidLookupUrl))
        val plain = save(AppSettings(ipLookupUrl = "http://api.ipify.org"))
        assertThat(plain).isEqualTo(DomainResult.Failure(SettingsError.InvalidLookupUrl))
        val good = save(AppSettings(ipLookupUrl = " https://api.ipify.org/path "))
        assertThat(good).isInstanceOf(DomainResult.Success::class.java)
        assertThat(repository.state.value.ipLookupUrl).isEqualTo("https://api.ipify.org/path")
    }
    @Test
    fun `external app delay is kept within 0 to 5 seconds`() = runTest {
        val repository = FakeSettingsRepository()
        val save = SaveSettingsUseCase(repository)
        val delay = GetExternalAppDelayUseCase(repository)
        assertThat(delay()).isEqualTo(0.seconds)
        save(AppSettings(externalAppDelaySeconds = 9))
        assertThat(repository.state.value.externalAppDelaySeconds).isEqualTo(5)
        save(AppSettings(externalAppDelaySeconds = -1))
        assertThat(repository.state.value.externalAppDelaySeconds).isEqualTo(0)
        save(AppSettings(externalAppDelaySeconds = 2))
        assertThat(delay()).isEqualTo(2.seconds)
    }
    @Test
    fun `external app delay survives the preferences round trip`() {
        val prefs = mutablePreferencesOf()
        prefs.write(AppSettings(externalAppDelaySeconds = 4))
        assertThat(prefs.toAppSettings().externalAppDelaySeconds).isEqualTo(4)
        assertThat(emptyPreferences().toAppSettings().externalAppDelaySeconds).isEqualTo(0)
        prefs[SettingsPreferences.EXTERNAL_APP_DELAY] = 42
        assertThat(prefs.toAppSettings().externalAppDelaySeconds).isEqualTo(5)
    }
    @Test
    fun `renaming keeps a single entry and refuses taken nicknames`() = runTest {
        val repository = FakeConfigRepository(listOf(sampleConfig("home"), sampleConfig("office")))
        val save = SaveConfigUseCase(ValidateConfigUseCase(), repository)
        val renamed: ConfigDraft = sampleConfig("home").toDraft().copy(nickname = "cottage")
        save(renamed, previous = Nickname("home"))
        assertThat(repository.configs.value.map { it.nickname.value }).containsExactly("office", "cottage")
        val clash = save(renamed.copy(nickname = "office"), previous = Nickname("cottage"))
        assertThat(clash).isEqualTo(DomainResult.Failure(ConfigError.NicknameTaken))
    }
    @Test
    fun `timer plan depends on keep open and notification setting`() = runTest {
        val scheduler = FakeKnockTimerScheduler()
        val settings = FakeSettingsRepository()
        val schedule = ScheduleKnockTimerUseCase(settings, scheduler)
        val keepOpen = sampleConfig(access = AccessRule.OpenPort(PortAccess("tcp/22", 60, keepOpen = true)))
        schedule(KnockOutcome(KnockRequest(sampleConfig()), SOURCE))
        schedule(KnockOutcome(KnockRequest(keepOpen), SOURCE))
        settings.state.value = AppSettings(notificationsEnabled = false)
        schedule(KnockOutcome(KnockRequest(keepOpen), SOURCE))
        assertThat(scheduler.plans).containsExactly(
            KnockTimerPlan.Countdown(Nickname("home"), 30),
            KnockTimerPlan.KeepOpen(KnockRequest(keepOpen), 60),
        ).inOrder()
    }
    @Test
    fun `keep open is refused without notifications and falls back when the service is refused`() = runTest {
        val scheduler = FakeKnockTimerScheduler(notificationsAllowed = false)
        val schedule = ScheduleKnockTimerUseCase(FakeSettingsRepository(), scheduler)
        val keepOpen = KnockOutcome(
            KnockRequest(sampleConfig(access = AccessRule.OpenPort(PortAccess("tcp/22", 60, keepOpen = true)))),
            SOURCE,
        )
        assertThat(schedule(keepOpen)).isEqualTo(KnockTimerOutcome.NOTIFICATIONS_BLOCKED)
        assertThat(scheduler.plans).isEmpty()
        scheduler.notificationsAllowed = true
        scheduler.keepOpenAllowed = false
        assertThat(schedule(keepOpen)).isEqualTo(KnockTimerOutcome.KEEP_OPEN_UNAVAILABLE)
        assertThat(scheduler.plans).containsExactly(KnockTimerPlan.Countdown(Nickname("home"), 60))
    }
    @Test
    fun `storage failures surface as typed errors when saving`() = runTest {
        val repository = FakeConfigRepository().apply { storageError = IllegalStateException("disk full") }
        val result = SaveConfigUseCase(ValidateConfigUseCase(), repository)(sampleConfig().toDraft(), previous = null)
        assertThat((result as DomainResult.Failure).error).isInstanceOf(ConfigError.Storage::class.java)
    }
}

private val SOURCE = ResolvedSourceIp("198.51.100.4", SourceIpMethod.Fixed)
