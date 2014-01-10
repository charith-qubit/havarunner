package com.github.havarunner

import java.lang.annotation.Annotation
import java.lang.reflect.{AccessibleObject, Field, Method}
import com.github.havarunner.exception.ConstructorNotFound

/**
 * Place here code that is related to Java reflections but not to parsing tests.
 */
private[havarunner] object Reflections {
  def findAnnotationRecursively(clazz: Class[_], annotationClass: Class[_ <: Annotation]): Option[Annotation] =
    Option(clazz.getAnnotation(annotationClass)) orElse {
      Option(clazz.getSuperclass) flatMap {
        superclass => findAnnotationRecursively(superclass, annotationClass)
      }
    }

  def instantiate(implicit suiteInstanceOption: Option[HavaRunnerSuite[_]], testAndParameters: TestAndParameters) = {
    val (constructor, argsOption) = resolveConstructorAndArgs
    val accessibleConstructor = ensureAccessible(constructor)
    argsOption match {
      case Some(args) => accessibleConstructor.newInstance(args.toSeq.asInstanceOf[Seq[AnyRef]]:_*)
      case None       => accessibleConstructor.newInstance()
    }
  }

  def findDeclaredClasses(clazz: Class[_], accumulator: Seq[Class[_]] = Seq()): Seq[Class[_]] =
    if (clazz.getDeclaredClasses.isEmpty) {
      clazz +: accumulator
    } else {
      clazz +: clazz.getDeclaredClasses.flatMap(findDeclaredClasses(_, accumulator))
    }

  def resolveConstructorAndArgs(implicit suiteInstanceOption: Option[HavaRunnerSuite[_]], testAndParameters: TestAndParameters) =
    withHelpfulConstructorMissingReport {
      val clazz = testAndParameters.testClass
      (suiteInstanceOption, testAndParameters.scenario) match {
        case (Some(suite), Some(scenario)) =>
          (
            clazz.getDeclaredConstructor(suite.suiteObject.getClass, scenario.getClass),
            Some(suite.suiteObject :: scenario :: Nil)
          )
        case (Some(suite), None) =>
          (
            clazz.getDeclaredConstructor(suite.suiteObject.getClass),
            Some(suite.suiteObject :: Nil)
          )
        case (None, Some(scenario)) =>
          (
            clazz.getDeclaredConstructor(scenario.getClass),
            Some(scenario :: Nil)
          )
        case (None, None) =>
          (clazz.getDeclaredConstructor(), None)
      }
    }

  def withHelpfulConstructorMissingReport[T](op: => T)(implicit testAndParameters: TestAndParameters) =
    try {
      op
    } catch {
      case e: NoSuchMethodException =>
        throw new ConstructorNotFound(testAndParameters.testClass, e)
    }

  def findMethods(clazz: Class[_], annotation: Class[_ <: Annotation]): Seq[Method] =
    classWithSuperclasses(clazz).flatMap(clazz =>
      clazz.getDeclaredMethods.filter(_.getAnnotation(annotation) != null)
    )

  def findFields(clazz: Class[_], annotation: Class[_ <: Annotation]): Seq[Field] =
    classWithSuperclasses(clazz).flatMap(clazz =>
      clazz.getDeclaredFields.filter(_.getAnnotation(annotation) != null)
    )

  def invoke(method: Method)(implicit testInstance: TestInstance) =
    ensureAccessible(method).invoke(testInstance.instance)

  def invokeEach(methods: Seq[Method])(implicit testInstance: TestInstance) {
    methods.foreach(invoke)
  }

  def classWithSuperclasses(clazz: Class[_], superclasses: Seq[Class[_]] = Nil): Seq[Class[_]] =
    if (clazz.getSuperclass != null)
      classWithSuperclasses(clazz.getSuperclass, clazz +: superclasses)
    else
      superclasses

  def hasMethodAnnotatedWith(clazz: Class[_], annotationClass: Class[_ <: Annotation]) =
    classWithSuperclasses(clazz).
      flatMap(_.getDeclaredMethods).
      exists(_.getAnnotation(annotationClass) != null)

  def ensureAccessible[T <: AccessibleObject](accessibleObject: T) = {
    accessibleObject.setAccessible(true)
    accessibleObject
  }
}
