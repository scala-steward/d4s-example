package leaderboard

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import distage.Activation
import distage.plugins.PluginConfig
import izumi.distage.model.definition.{Axis, DIResource}
import izumi.distage.roles.model.{RoleDescriptor, RoleService}
import izumi.distage.roles.{RoleAppLauncher, RoleAppMain}
import izumi.functional.bio.{BIO, BlockingIO}
import izumi.fundamentals.platform.cli.model.raw.{RawEntrypointParams, RawRoleParams}
import leaderboard.config.DynamoCfg
import leaderboard.dynamo.java.DynamoHelper
import leaderboard.dynamo.scanamo.ScanamoUtils
import leaderboard.effects.{ConcurrentThrowable, TTimer}
import leaderboard.http.HttpApi
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

final class LeaderboardServiceRole[F[+_, +_]: ConcurrentThrowable: TTimer: BIO: BlockingIO](
  dynamoClient: DynamoDbClient,
  amzClinet: AmazonDynamoDB,
  cfg: DynamoCfg,
  httpApi: HttpApi[F]
) extends RoleService[F[Throwable, ?]] {
  override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): DIResource.DIResourceBase[F[Throwable, ?], Unit] = {
    for {
      _ <- DynamoHelper.tableSetUp(dynamoClient, cfg)
      _ <- ScanamoUtils.tableSetUp(amzClinet, cfg)
      _ <- DIResource.fromCats {
        BlazeServerBuilder[F[Throwable, ?]]
          .withHttpApp(httpApi.routes.orNotFound)
          .bindLocal(8080)
          .resource
      }
    } yield ()
  }
}

object MainProdAmz extends MainBase(Activation(CustomAxis -> CustomAxis.Amz))

object MainProdScanamo extends MainBase(Activation(CustomAxis -> CustomAxis.Scanamo))

object MainProdScanamoAlpakka extends MainBase(Activation(CustomAxis -> CustomAxis.Alpakka))

object MainDummy extends MainBase(Activation(CustomAxis -> CustomAxis.Dummy))

object LeaderboardServiceRole extends RoleDescriptor {
  val id = "leaderboard"
}

//object GenericLauncher extends MainBase(Activation(Repo -> Repo.Prod)) {
//  override val requiredRoles = Vector.empty
//}

sealed abstract class MainBase(activation: Activation)
  extends RoleAppMain.Default(
    launcher = new RoleAppLauncher.LauncherBIO[zio.IO] {
      override val pluginConfig        = PluginConfig.cached(packagesEnabled = Seq("leaderboard.plugins"))
      override val requiredActivations = activation
    }
  ) {
  override val requiredRoles = Vector(
    RawRoleParams(LeaderboardServiceRole.id)
  )
}

object CustomAxis extends Axis {
  override def name: String = "custom-axis"
  case object Amz extends AxisValueDef
  case object Scanamo extends AxisValueDef
  case object D4S extends AxisValueDef
  case object Alpakka extends AxisValueDef
  case object Dummy extends AxisValueDef
}
