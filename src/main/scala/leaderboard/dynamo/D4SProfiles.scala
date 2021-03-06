package leaderboard.dynamo

import d4s.DynamoConnector
import izumi.functional.bio.Bifunctor2
import leaderboard.dynamo.ProfilesTable.UserProfileWithIdStored
import leaderboard.models.common.UserId
import leaderboard.models.{QueryFailure, UserProfile}
import leaderboard.repo.Profiles

final class D4SProfiles[F[+_, +_]: Bifunctor2](
  connector: DynamoConnector[F],
  profilesTable: ProfilesTable
) extends Profiles[F] {

  import profilesTable._

  override def getProfile(userId: UserId): F[QueryFailure, Option[UserProfile]] = {
    connector
      .run("get-profile") {
        table
          .getItem(mainFullKey(userId))
          .decodeItem[UserProfile]
      }.leftMap(err => QueryFailure(err.message, err.cause))
  }

  override def setProfile(userId: UserId, profile: UserProfile): F[QueryFailure, Unit] = {
    connector
      .run("set-profile") {
        table.updateItem(UserProfileWithIdStored(userId.value, profile.userName, profile.description)).void
      }.leftMap(err => QueryFailure(err.message, err.cause))
  }
}
