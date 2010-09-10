hotpotato
=========

0.7.2
-----
* Removed dependency on Netty's internal logging and added SLF4J

0.7.1
-----
* Fixed bug in DefaultHttpConnection where HttpHeaders.isKeepAlive was being used on request, rather than response.
* Added support for HTTP Sessions: way simpler API, perfect for client-side usage (package org.factor45.hotpotato.session)
  - cookie storage;
  - authentication (still needs proper validation, but it's hard to find systems which use Digest auth nowadays...);
  - automatic redirection;
  - proxy support.

0.7.0
-----
* First release!

