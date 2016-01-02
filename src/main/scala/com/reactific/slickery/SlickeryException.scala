package com.reactific.slickery

import com.reactific.helpers.{LoggingHelper, ThrowableWithComponent}

/** Exceptions that Slickery throws via ThrowingHelper */
class SlickeryException(val component : LoggingHelper, msg : String, cause: Option[Throwable] = None)
  extends Exception(msg, cause.orNull) with ThrowableWithComponent
