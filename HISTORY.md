hotpotato
=========

0.7.1
-----
* Fixed bug in DefaultHttpConnection where HttpHeaders.isKeepAlive was being used on request, rather than response.
* Added support for HTTP Sessions: way simpler API, perfect for client-side usage (package org.factor45.hotpotato.session)
  - cookie storage;
  - authentication;
  - automatic redirection;
  - proxy support.

0.7.0
-----
* First release!