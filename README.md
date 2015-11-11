
[![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)
[![Build Status](https://travis-ci.org/reactific/slickery.svg?branch=master)](https://travis-ci.org/reactific/slickery)
[![Coverage Status](https://coveralls.io/repos/reactific/slickery/badge.svg?branch=master)](https://coveralls.io/r/reactific/slickery?branch=master)
[![Release](https://img.shields.io/github/release/reactific/slickery.svg?style=flat)](https://github.com/reactific/slickery/releases)
[![Downloads](https://img.shields.io/github/downloads/reactific/slickery/latest/total.svg)](https://github.com/reactific/slickery/releases)

[![Join the chat at https://gitter.im/reactific/slickery](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/reactific/slickery?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

# slickery

A set of utilities for working with Typesafe Slick 3.X. It provides the following:
* Some "Ables" traits for common data representations of database objects
* Definition of an abstract Schema which defines fragments of table rows, queries, mapped types, etc. 
* The building blocks for DRY implementation of database schemas. 
* Other common and repeated utilities for working with Slick 3.X
* Abstractions for keeping database-specific code out of your application.

The intent is to provide a consistent set of frequently used features that can be implemented
in a variety of relational databases. This is intended for applications that need to support
multiple database types without letting their differences creep into application code. The 
differences are managed in slickery. 
