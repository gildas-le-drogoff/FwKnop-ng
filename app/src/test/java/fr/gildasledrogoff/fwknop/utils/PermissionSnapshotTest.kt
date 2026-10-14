// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.utils
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.utils.helpers.LocalNetworkAccess
import fr.gildasledrogoff.fwknop.utils.helpers.PermissionSnapshot
import fr.gildasledrogoff.fwknop.utils.helpers.PermissionStatus
import fr.gildasledrogoff.fwknop.utils.helpers.RuntimePermissions
import org.junit.Test
class PermissionSnapshotTest {
    @Test
    fun `a denied permission can be prompted unless already asked`() {
        val snapshot = PermissionSnapshot(notifications = PermissionStatus.DENIED)
        assertThat(snapshot.canPrompt(RuntimePermissions.NOTIFICATIONS)).isTrue()
        assertThat(snapshot.copy(prompted = setOf(RuntimePermissions.NOTIFICATIONS)).canPrompt(RuntimePermissions.NOTIFICATIONS))
            .isFalse()
    }
    @Test
    fun `a granted or refused for good permission is never prompted`() {
        val granted = PermissionSnapshot(notifications = PermissionStatus.GRANTED)
        val refusedForGood = PermissionSnapshot(notifications = PermissionStatus.DENIED_PERMANENTLY)
        assertThat(granted.canPrompt(RuntimePermissions.NOTIFICATIONS)).isFalse()
        assertThat(refusedForGood.canPrompt(RuntimePermissions.NOTIFICATIONS)).isFalse()
    }
    @Test
    fun `statuses are read per permission`() {
        val snapshot = PermissionSnapshot(
            notifications = PermissionStatus.GRANTED,
            localNetwork = PermissionStatus.DENIED_PERMANENTLY,
        )
        assertThat(snapshot.status(RuntimePermissions.NOTIFICATIONS)).isEqualTo(PermissionStatus.GRANTED)
        assertThat(snapshot.status(LocalNetworkAccess.PERMISSION)).isEqualTo(PermissionStatus.DENIED_PERMANENTLY)
        assertThat(snapshot.status("android.permission.CAMERA")).isEqualTo(PermissionStatus.GRANTED)
    }
}
