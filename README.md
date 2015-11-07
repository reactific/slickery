# slickery

A set of utilities for working with Typesafe Slick 3.X. It provides the following:
* Definition of a Component which permits a set of Tables and Queries on them to be defined
* A variety of base classes for Tables and Queries
* A Schema class for collecting components together and validating their schema

The intent is to provide a consistent set of frequently used features that can be implemented
in a variety of relational databases. This is intended for applications that need to support
multiple database types while only using one at a time.
