package com.reactific.slickery

import com.reactific.helpers.{HelperComponent, ThrowableWithComponent}

trait SlickeryComponent extends HelperComponent {

  override protected def mkThrowable(msg: String, cause : Option[Throwable] = None) : ThrowableWithComponent = {
    new SlickeryException(this, msg, cause)
  }

}
