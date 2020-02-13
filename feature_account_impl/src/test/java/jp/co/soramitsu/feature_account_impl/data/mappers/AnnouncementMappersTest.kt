package jp.co.soramitsu.feature_account_impl.data.mappers

import jp.co.soramitsu.core_db.model.AnnouncementLocal
import jp.co.soramitsu.feature_account_api.domain.model.ActivityFeedAnnouncement
import jp.co.soramitsu.feature_account_impl.data.network.model.AnnouncementRemote
import junit.framework.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class AnnouncementMappersTest {

    private val announcementRemote = AnnouncementRemote(
        "message",
        "100"
    )
    private val announcement = ActivityFeedAnnouncement(
        "message",
        "100"
    )
    private val announcementLocal = AnnouncementLocal(
        0,
        "message",
        "100"
    )

    @Test
    fun `map announcement remote to announcement called`() {
        assertEquals(announcement, mapAnnoucementRemoteToAnnouncement(announcementRemote))
    }

    @Test
    fun `map announcement to announcement local called`() {
        assertEquals(announcementLocal, mapAnnouncementToAnnouncementLocal(announcement))
    }

    @Test
    fun `map announcement local to announcement called`() {
        assertEquals(announcement, mapAnnouncementLocalToAnnouncement(announcementLocal))
    }
}