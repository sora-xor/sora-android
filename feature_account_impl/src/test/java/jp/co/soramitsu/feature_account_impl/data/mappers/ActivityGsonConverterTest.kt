package jp.co.soramitsu.feature_account_impl.data.mappers

import com.google.gson.Gson
import com.google.gson.JsonObject
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_account_api.domain.model.ActivityFeed
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.test_shared.anyNonNull
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.util.Date

@RunWith(MockitoJUnitRunner::class)
class ActivityGsonConverterTest {

    @Mock
    private lateinit var resourceManager: ResourceManager
    @Mock
    private lateinit var numbersFormatter: NumbersFormatter

    private lateinit var activityGsonConverter: ActivityGsonConverter

    @Before fun setup() {
        given(resourceManager.getString(R.string.activity_user_registered_type)).willReturn("Friend Registered")
        given(resourceManager.getString(R.string.activity_user_registered_title_template)).willReturn("%s %s")
        given(resourceManager.getString(R.string.activity_user_registered_description)).willReturn("Your reputation will be increased!")
        given(resourceManager.getString(R.string.activity_xor_between_users_transferred_type_template)).willReturn("XOR Received")
        given(resourceManager.getString(R.string.activity_voting_rights_credited_type_template)).willReturn("Votes distribution")
        given(resourceManager.getString(R.string.activity_voting_rights_title_template)).willReturn("Daily votes")
        given(resourceManager.getString(R.string.activity_project_funded_type_template)).willReturn("Project has been completed!")
        given(resourceManager.getString(R.string.activity_project_funded_title_template)).willReturn("%s")
        given(resourceManager.getString(R.string.activity_project_funded_description_template)).willReturn("You will receive information about XOR reward soon")
        given(resourceManager.getString(R.string.activity_project_created_type_template)).willReturn("New project!")
        given(resourceManager.getString(R.string.activity_project_created_title_template)).willReturn("%s")
        given(resourceManager.getString(R.string.activity_project_created_description_template)).willReturn("%s")
        given(resourceManager.getString(R.string.activity_project_closed_type_template)).willReturn("Project reached deadline!")
        given(resourceManager.getString(R.string.activity_project_closed_title_template)).willReturn("%s")
        given(resourceManager.getString(R.string.activity_xor_reward_credited_from_project_type_template)).willReturn("XOR Reward for voting")
        given(resourceManager.getString(R.string.activity_voted_friend_added_type_template)).willReturn("Friend voted for project")
        given(resourceManager.getString(R.string.activity_voted_friend_added_title_template)).willReturn("%s %s")
        given(resourceManager.getString(R.string.activity_voted_friend_added_description_template)).willReturn("%s has spent %s votes for %s project")
        given(resourceManager.getString(R.string.activity_user_rank_changed_type_template)).willReturn("Rank changed")
        given(resourceManager.getString(R.string.activity_user_rank_changed_title_template)).willReturn("Your rank now is %s (of %s))")
        given(resourceManager.getString(R.string.activity_project)).willReturn("Project")
        given(resourceManager.getString(R.string.activity_user)).willReturn("User")

        activityGsonConverter = ActivityGsonConverter(resourceManager, numbersFormatter)
    }

    @Test fun `convert activity items called`() {
        val gson = Gson()
        val projectJson = gson.fromJson("{\"87670661-0ff5-4966-96a5-99ec70710419\":{\"projectName\":\"Arstotzka\"},\"3b2f4701-5e72-464d-9cfe-7bfff1aeed74\":{\"projectName\":\"Name\"}}", JsonObject::class.java)
        val usersJson = gson.fromJson("{\"did:sora:45802d5bd158878b292c\":{\"firstName\":\"Ivan\",\"lastName\":\"Ivanov\"}}", JsonObject::class.java)

        val eventsJsonList = mutableListOf(
            gson.fromJson("{\"issuedBy\":\"\",\"issuedAt\":0,\"type\":\"VotingRightsCredited\",\"userId\":\"did:sora:45802d5bd158878b292c\",\"votingRights\":\"3.9472753374225\"}", JsonObject::class.java),
            gson.fromJson("{\"issuedAt\":0,\"type\":\"FriendRegistered\",\"userId\":\"did:sora:45802d5bd158878b292c\"}", JsonObject::class.java),
            gson.fromJson("{\"issuedAt\":0,\"type\":\"XORBetweenUsersTransferred\",\"receiver\":\"did:sora:45802d5bd158878b292c\",\"sender\":\"did:sora:45802d5bd158878b292c\",\"amount\":10}", JsonObject::class.java),
            gson.fromJson("{\"issuedBy\":\"\",\"issuedAt\":0,\"type\":\"UserRankChanged\",\"userId\":\"did:sora:45802d5bd158878b292c\",\"rank\":29,\"totalRank\":35}", JsonObject::class.java),
            gson.fromJson("{\"issuedBy\":\"\",\"issuedAt\":0,\"type\":\"ProjectCreated\",\"projectId\":\"3b2f4701-5e72-464d-9cfe-7bfff1aeed74\",\"name\":\"Name\",\"description\":\"Short description\",\"detailedDescription\":\"Detailed description\",\"projectLink\":\"http://www.bbc.com\",\"imageLink\":\"https://sora-projects-static-dev.s3.amazonaws.com/b4ae2458332378419f8132f9abb69e7c14c62a72ee191cc36d86ae1464d4622d\",\"fundingTarget\":\"1000\",\"fundingDeadline\":1577533679,\"email\":\"test@mail.com\",\"walletAccountId\":\"noir@sora\"}", JsonObject::class.java),
            gson.fromJson("{\"issuedAt\":0,\"type\":\"ProjectFunded\",\"projectId\":\"87670661-0ff5-4966-96a5-99ec70710419\"}", JsonObject::class.java),
            gson.fromJson("{\"issuedAt\":0,\"type\":\"ProjectClosed\",\"projectId\":\"87670661-0ff5-4966-96a5-99ec70710419\"}", JsonObject::class.java),
            gson.fromJson("{\"issuedAt\":0,\"type\":\"XORRewardCreditedFromProject\",\"projectId\":\"87670661-0ff5-4966-96a5-99ec70710419\",\"reward\":550,\"userId\":\"did:sora:45802d5bd158878b292c\"}", JsonObject::class.java),
            gson.fromJson("{\"issuedAt\":0,\"type\":\"UserVotedForProject\",\"projectId\":\"87670661-0ff5-4966-96a5-99ec70710419\",\"givenVotes\":100,\"potentialRewar\":100,\"userId\":\"did:sora:45802d5bd158878b292c\"}", JsonObject::class.java)
        )

        val activities = mutableListOf(
            ActivityFeed(
                "Votes distribution",
                "Daily votes",
                "",
                "1",
                Date(0),
                R.drawable.icon_activity_vote,
                R.drawable.heart_shape
            ),
            ActivityFeed(
                "Friend Registered",
                "Ivan Ivanov",
                "Your reputation will be increased!",
                "",
                Date(0),
                R.drawable.icon_activity_invite,
                -1
            ),
            ActivityFeed(
                "XOR Received",
                "Ivan Ivanov",
                "",
                "1",
                Date(0),
                R.drawable.icon_activity_xor,
                -1),
            ActivityFeed(
                "Rank changed",
                "Your rank now is 29 (of 35))",
                "",
                "",
                Date(0),
                R.drawable.icon_activity_reputation,
                -1
            ),
            ActivityFeed(
                "New project!",
                "Name",
                "Short description",
                "",
                Date(0),
                R.drawable.icon_activity_project,
                -1
            ),
            ActivityFeed(
                "Project has been completed!",
                "Arstotzka",
                "You will receive information about XOR reward soon",
                "",
                Date(0),
                R.drawable.icon_activity_project,
                -1
            ),
            ActivityFeed(
                "Project reached deadline!",
                "Arstotzka",
                "",
                "",
                Date(0),
                R.drawable.icon_activity_project,
                -1
            ),
            ActivityFeed(
                "XOR Reward for voting",
                "Arstotzka",
                "",
                "1",
                Date(0),
                R.drawable.icon_activity_xor,
                -1
            ),
            ActivityFeed(
                "Friend voted for project",
                "Ivan Ivanov",
                "Ivan has spent 1 votes for Arstotzka project",
                "",
                Date(0),
                R.drawable.icon_activity_vote,
                -1
            )
            )

        given(numbersFormatter.formatInteger(anyNonNull())).willReturn("1")
        given(numbersFormatter.formatBigDecimal(anyNonNull())).willReturn("1")

        assertEquals(activities, activityGsonConverter.convertActivityItems(eventsJsonList, projectJson, usersJson, "did:sora:45802d5bd158878b292c"))
    }
}