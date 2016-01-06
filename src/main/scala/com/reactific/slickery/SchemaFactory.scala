package com.reactific.slickery

import com.typesafe.config.Config

trait SchemaFactory[DRVR <: SlickeryDriver] {

  def apply(name : String, config : Config)
}
