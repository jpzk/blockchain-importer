lazy val commonSettings = Seq(
  organization := "jpzk",
  version := "1.0.0",
  scalaVersion := "2.12.10",
  description := "blockchainimporter"
)

lazy val extractor = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    assemblyJarName in assembly := "importer.jar",
    publishMavenStyle := false,
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", "io.netty.versions.properties", _*) =>
        MergeStrategy.first
      case PathList("com", "typesafe", "scalalogging", _*) =>
        MergeStrategy.first
      case PathList("org", "xerial", _*)             => MergeStrategy.first
      case PathList("com", "sksamuel", "avro4s", _*) => MergeStrategy.first
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
    )
  .enablePlugins(DockerPlugin)
  .settings(buildOptions in docker := BuildOptions(cache = false))
  .settings(
    imageNames in docker := Seq(
      ImageName(s"${organization.value}/blockchainimporter:${version.value}")
    )
  )
  .settings(
    dockerfile in docker := {
      val artifact: File = assembly.value
      val artifactTargetPath = s"/app/${artifact.name}"
      new Dockerfile {
        from("anapsix/alpine-java")
        add(artifact, artifactTargetPath)
        copy(
          baseDirectory(_ / "src" / "main" / "resources" / "logback.xml").value,
          file("/logback.xml")
        )
        entryPoint(
          "java",
          "-Dlogback.configurationFile=/logback.xml",
          "-cp",
          artifactTargetPath,
          "com.madewithtea.blockchainimporter.Main"
        )
      }
    }
  )
  .settings(
    resolvers ++= Seq(
      "confluent.io" at "https://packages.confluent.io/maven/"
    )
  )
  .settings(
    libraryDependencies ++= avro ++ testDeps ++ kafka ++ log ++ slick ++ cats ++ rpc
  )

val workaround = {
  sys.props += "packaging.type" -> "jar"
  ()
}

lazy val rpc =  Seq("com.madewithtea" %% "blockchain-rpc" % "2.5.2")
lazy val avro = Seq(
  "com.sksamuel.avro4s" %% "avro4s-core" % "3.0.1",
  "com.sksamuel.avro4s" %% "avro4s-macros" % "2.0.4"
)

lazy val kafka = Seq(
  "org.apache.kafka" % "kafka-clients" % "2.0.0",
  "io.confluent" % "kafka-avro-serializer" % "5.0.0"
    excludeAll (
      ExclusionRule(organization = "org.slf4j"),
      ExclusionRule(organization = "log4j")
  )
)

lazy val slick = Seq(
  "com.typesafe.slick" %% "slick" % "3.3.2",
  "org.postgresql" % "postgresql" % "42.2.6"
)

lazy val testDeps = Seq(
  "org.scalatest" %% "scalatest" % "3.0.4" % Test,
  "org.scalamock" %% "scalamock" % "4.1.0" % Test
)

lazy val log = Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "net.logstash.logback" % "logstash-logback-encoder" % "4.11",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0"
)


lazy val cats = Seq(
  "org.typelevel" %% "cats-effect" % "2.0.0"
)
