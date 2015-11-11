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
