package com.eigengo.lift.exercise

import akka.actor.Props
import akka.contrib.pattern.ShardRegion
import akka.persistence.PersistentView
import com.eigengo.lift.common.UserId
import com.eigengo.lift.exercise.UserExercises.{SessionEndedEvt, SessionStartedEvt, ExerciseEvt}

object UserExercisesStatistics {
  /** The shard name */
  val shardName = "user-exercises-statistics"
  /** The props to create the actor on a node */
  def props() = Props(classOf[UserExercisesStatistics])

  val supportedMuscleGroups = List(
    MuscleGroup(key = "legs",  title = "Legs",  exercises = List("squat", "leg press", "leg extension", "leg curl", "lunge")),
    MuscleGroup(key = "core",  title = "Core",  exercises = List("crunch", "side bend", "cable crunch", "sit up", "leg raises")),
    MuscleGroup(key = "back",  title = "Back",  exercises = List("pull up", "row", "deadlift", "hyper-extension")),
    MuscleGroup(key = "arms",  title = "Arms",  exercises = List("bicep curl", "hammer curl", "pronated curl", "tricep push down", "tricep overhead extension", "tricep dip", "close-grip bench press")),
    MuscleGroup(key = "chest", title = "Chest", exercises = List("chest press", "butterfly", "cable cross-over", "incline chest press", "push up")),
    MuscleGroup(key = "shoulders", title = "Shoulders", exercises = List("shoulder press", "lateral raise", "front raise", "rear raise", "upright row", "shrug")),
    MuscleGroup(key = "cardiovascular", title = "Cardiovascular", exercises = List("running", "cycling", "swimming", "elliptical", "rowing"))
  )
  // all exercises
  val allExercises: List[Exercise] = supportedMuscleGroups.flatMap(_.exercises).map(name ⇒ Exercise(name, None, None))

  object ExerciseStatistics {
    case class Entry(key: MuscleGroupKey, intendedIntensity: ExerciseIntensity, count: Int, exercise: Exercise) {
      def inc(): Entry = copy(count = count + 1)

      def matches(sessionProperties: SessionProperties, exercise: Exercise): Boolean = {

        def matches(e1: Exercise, e2: Exercise): Boolean = {
          e1.name == e2.name && ((e1.intensity, e2.intensity) match {
            case (Some(i1), Some(i2)) ⇒ i1 ~~ i2
            case _ ⇒ false
          })
        }

        sessionProperties.muscleGroupKeys.contains(key) &&
        intendedIntensity ~~ sessionProperties.intendedIntensity &&
        matches(this.exercise, exercise)
      }
    }

    /** Empty ExerciseStatistics */
    val empty: ExerciseStatistics = ExerciseStatistics(List.empty)
  }

  /**
   * Maintains exercise statistics
   * @param statistics the statistics
   */
  case class ExerciseStatistics(statistics: List[ExerciseStatistics.Entry]) {
    import ExerciseStatistics._

    /**
     * Compute the exerises ordered by their counts, with intensity set to average intensity in the set
     * identified by the filter
     * @param filter the filtering function
     * @return the exercises, ordered by count
     */
    private def examples(filter: Entry ⇒ Boolean): List[Exercise] = {
      val userExercises = statistics.filter(filter).sortBy(_.count).groupBy(_.exercise.name).map {
        case (name, entries) ⇒ Exercise(name, Some(entries.map(_.exercise.intensity.getOrElse(0.5)).sum / entries.size), None)
      }.toList

      val rest = allExercises.filterNot(userExercises.contains)

      userExercises ++ rest
    }

    /**
     * Examples filtered by the given muscle groups and intended intensity
     * @param muscleGroups the muscle groups
     * @param intendedIntensity the intended intensity
     * @return
     */
    def examples(muscleGroups: Seq[MuscleGroupKey], intendedIntensity: Double): List[Exercise] = {
      examples(e ⇒ muscleGroups.contains(e.key) && e.intendedIntensity ~~ intendedIntensity)
    }

    /**
     * Examples filtered by the given muscle groups
     * @param muscleGroups the muscle groups 
     * @return the examples
     */
    def examples(muscleGroups: Seq[MuscleGroupKey]): List[Exercise] = {
      examples(e ⇒ muscleGroups.contains(e.key))
    }

    /**
     * Exercise examples across all exercises
     * @return the examples
     */
    def examples(): List[Exercise] = {
      examples(_ ⇒ true)
    }

    def withNewExercise(sessionProperties: SessionProperties, exercise: Exercise): ExerciseStatistics = {
      ExerciseStatistics(statistics.map { e ⇒ if (e.matches(sessionProperties, exercise)) e.inc() else e })
    }

  }

  /**
   * Get the classification examples for the given user and session
   * @param userId the user identity
   * @param sessionId the session identity
   */
  case class UserExerciseExplicitClassificationExamples(userId: UserId, sessionId: Option[SessionId])

  /**
   * Obtain list of classification examples
   * @param sessionId the session
   */
  private case class ExerciseExplicitClassificationExamples(sessionId: Option[SessionId])

  /**
   * The id extractor
   */
  val idExtractor: ShardRegion.IdExtractor = {
    case UserExerciseExplicitClassificationExamples(userId, sessionId) ⇒ (userId.toString, ExerciseExplicitClassificationExamples(sessionId))
  }

  /**
   * The shard resolver
   */
  val shardResolver: ShardRegion.ShardResolver = {
    case UserExerciseExplicitClassificationExamples(userId, _) ⇒ s"${userId.hashCode() % 10}"
  }

}

class UserExercisesStatistics extends PersistentView {
  import scala.concurrent.duration._
  import UserExercisesStatistics._
  private val userId = UserId(self.path.name)
  private var exerciseStatistics: ExerciseStatistics = ExerciseStatistics.empty

  // we'll hang around for 360 seconds, just like the exercise sessions
  context.setReceiveTimeout(360.seconds)

  override def autoUpdateInterval: FiniteDuration = 1.second
  override def autoUpdate: Boolean = true

  override val viewId: String = s"user-exercises-statistics-${userId.toString}"
  override val persistenceId: String = s"user-exercises-${userId.toString}"

  lazy val queries: Receive = {
    case ExerciseExplicitClassificationExamples(None) ⇒ sender() ! exerciseStatistics.examples()
      // TODO: This is not the right handler: it should filter
    case ExerciseExplicitClassificationExamples(_)    ⇒ sender() ! exerciseStatistics.examples()
  }

  lazy val notExercising: Receive = {
    case SessionStartedEvt(sessionId, sessionProperties) if isPersistent ⇒
      context.become(exercising(sessionProperties))
  }

  private def exercising(sessionProperties: SessionProperties): Receive = {
    case ExerciseEvt(_, metadata, exercise) if isPersistent ⇒
      exerciseStatistics = exerciseStatistics.withNewExercise(sessionProperties, exercise)
    case SessionEndedEvt(_) if isPersistent ⇒ context.become(notExercising) 
  }

  override def receive: Receive = notExercising.orElse(queries)
}
