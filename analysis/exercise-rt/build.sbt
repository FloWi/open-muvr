import Dependencies._

Build.Settings.project

name := "exercise-rt"

libraryDependencies ++= Seq(
  // Codec
  scodec_bits,
  scalaz.core,
  // Spark
  spark.core,
  spark.mllib intransitive(),
  spark.streaming intransitive(),
  spark.streaming_kafka intransitive(),
  // Kafka
  kafka.kafka,
  // Testing
  scalatest % "test",
  scalacheck % "test"
)

val sparkRun = taskKey[Unit]("Submit the spark job.")

sparkRun := {
  val sparkHome = "/usr/local/Cellar/apache-spark/1.1.1"
  for {
    mainClass ← (selectMainClass in Compile).value
    fatJar    =  assembly.value
    cmd       =  s"$sparkHome/bin/spark-submit --class $mainClass $fatJar"
  } yield cmd!
}
