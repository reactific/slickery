package com.reactific.slickery

import com.reactific.helpers.{FutureHelper, ThrowingHelper, LoggingHelper, ThrowableWithComponent}

trait SlickeryComponent extends LoggingHelper with ThrowingHelper with FutureHelper {

  override protected def mkThrowable(msg: String, cause : Option[Throwable] = None) : ThrowableWithComponent = {
    new SlickeryException(this, msg, cause)
  }

}
