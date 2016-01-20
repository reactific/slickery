/**********************************************************************************************************************
  *                                                                                                                    *
  * Copyright (c) 2013, Reactific Software LLC. All Rights Reserved.                                                   *
  *                                                                                                                    *
  * Scrupal is free software: you can redistribute it and/or modify it under the terms                                 *
  * of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License,   *
  * or (at your option) any later version.                                                                             *
  *                                                                                                                    *
  * Scrupal is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied      *
  * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more      *
  * details.                                                                                                           *
  *                                                                                                                    *
  * You should have received a copy of the GNU General Public License along with Scrupal. If not, see either:          *
  * http://www.gnu.org/licenses or http://opensource.org/licenses/GPL-3.0.                                             *
  **********************************************************************************************************************/

package com.reactific.slickery

import slick.dbio.{NoStream, DBIOAction}

import scala.concurrent.{ExecutionContext, Future}

trait WithSchema {

  type SCHEMA_TYPE <: Schema[_]

  def executionContext : ExecutionContext
  def schema : SCHEMA_TYPE

  def withSchema[R](f : (SCHEMA_TYPE) ⇒ DBIOAction[R,NoStream,_]) : Future[R] = {
    implicit val ec = executionContext
    schema.db.run {
      f(schema)
    }
  }

  def mapQuery[R,S](query : (SCHEMA_TYPE) ⇒ DBIOAction[R,NoStream,_])(transform: (R,ExecutionContext) ⇒ S) : Future[S] = {
    implicit val ec = executionContext
    withSchema[R](query).map { result : R ⇒
      transform(result,ec)
    }
  }

  def flatMapQuery[R,S](query : (SCHEMA_TYPE) ⇒ DBIOAction[R,NoStream,_])(transform: (R,ExecutionContext) ⇒ Future[S]) : Future[S] = {
    implicit val ec = executionContext
    withSchema[R](query).flatMap { result : R ⇒
      transform(result,ec)
    }
  }

}
