ThisBuild / version := "0.80"

ThisBuild / scalaVersion := "2.13.10"

Compile / packageBin / mainClass := Some("com.guardian.Main")

lazy val root = (project in file("."))
  .settings(
    name := "guardian-mds-mme-translator",
    idePackagePrefix := Some("com.guardian")
  )

libraryDependencies += "org.apache.kafka"         %% "kafka-streams-scala" % "3.4.0"
dependencyOverrides += "org.apache.kafka"          % "kafka-clients"       % "3.4.0"
libraryDependencies += "io.monix"                 %% "monix-kafka-1x"      % "1.0.0-RC6"
libraryDependencies += "io.monix"                 %% "monix"               % "3.4.1"
libraryDependencies += "dev.zio"                  %% "zio"                 % "2.0.10"
libraryDependencies += "dev.zio"                  %% "zio-streams"         % "2.0.10"
libraryDependencies += "dev.zio"                  %% "zio-kafka"           % "2.1.3"
libraryDependencies += "dev.zio"                  %% "zio-interop-monix"   % "3.4.2.1.1"
libraryDependencies += "org.typelevel"            %% "cats-core"           % "2.9.0"
libraryDependencies += "io.lettuce"                % "lettuce-core"        % "6.2.2.RELEASE"
libraryDependencies += "com.github.jasync-sql"     % "jasync-mysql"        % "2.1.23"
libraryDependencies += "com.github.pureconfig"    %% "pureconfig"          % "0.17.2"
libraryDependencies += "org.apache.logging.log4j" %% "log4j-api-scala"     % "12.0"
libraryDependencies += "org.apache.logging.log4j"  % "log4j-api"           % "2.20.0"
libraryDependencies += "org.apache.logging.log4j"  % "log4j-core"          % "2.20.0" % "runtime"
libraryDependencies += "org.apache.logging.log4j"  % "log4j-slf4j2-impl"   % "2.20.0"
libraryDependencies += "org.scalatest"            %% "scalatest"           % "3.2.15" % "test"

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x                             => MergeStrategy.first
}
