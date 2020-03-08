package leaderboard.plugins

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import distage.config.ConfigModuleDef
import distage.plugins.PluginDef
import distage.{ModuleDef, TagKK}
import izumi.distage.model.definition.StandardAxis.Repo
import leaderboard.config.{DynamoCfg, ProvisioningCfg}
import leaderboard.dynamo.java.{AwsLadder, AwsProfiles, DynamoHelper}
import leaderboard.dynamo.scanamo.{ScanamoLadder, ScanamoUtils}
import leaderboard.http.HttpApi
import leaderboard.repo.{Ladder, Profiles, Ranks}
import leaderboard.{CustomAxis, LeaderboardServiceRole}
import org.http4s.dsl.Http4sDsl
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import zio.IO

object LeaderboardPlugin extends PluginDef {
  include(modules.roles[IO])
  include(modules.api[IO])
  include(modules.repoDummy[IO])
  include(modules.repoAmz[IO])
  include(modules.repoScanamo[IO])
  include(modules.clients)
  include(modules.configs)

  object modules {
    def roles[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
      make[LeaderboardServiceRole[F]]
    }

    def api[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
      make[HttpApi[F]].from[HttpApi.Impl[F]]
      make[Ranks[F]].from[Ranks.Impl[F]]

      make[Http4sDsl[F[Throwable, ?]]]
    }

    def repoDummy[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
      tag(CustomAxis.Dummy)

      make[Ladder[F]].fromResource[Ladder.Dummy[F]]
      make[Profiles[F]].fromResource[Profiles.Dummy[F]]
    }

    def repoAmz[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
      tag(CustomAxis.Amz)
      make[Ladder[F]].from[AwsLadder[F]].named("aws-ladder")
      make[Profiles[F]].from[AwsProfiles[F]].named("aws-profiles")
    }

    def repoScanamo[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
      tag(CustomAxis.Scanamo)
      make[Ladder[F]].from[ScanamoLadder[F]].named("scanamo-ladder")
      make[Profiles[F]].from[AwsProfiles[F]].named("scanamo-profiles")
    }

    val clients: ModuleDef = new ModuleDef {
      make[DynamoDbClient].from {
        cfg: DynamoCfg =>
          DynamoHelper.makeClient(cfg)
      }

      make[AmazonDynamoDB].from {
        cfg: DynamoCfg =>
          ScanamoUtils.makeClient(cfg)
      }
    }

    val configs: ConfigModuleDef = new ConfigModuleDef {
      makeConfig[DynamoCfg]("aws.dynamo")
      makeConfig[ProvisioningCfg]("aws.dynamo.provisioning")
    }
  }
}
