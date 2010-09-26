hotpotato History
=================

next release
------------
* Fixed bug in DefaultHttpConnection where HttpHeaders.isKeepAlive was being used on request, rather than response.
* Added support for HTTP Sessions: way simpler API, perfect for client-side usage (package org.factor45.hotpotato.session)
  - cookie storage;
  - authentication (still needs proper validation, but it's hard to find systems which use Digest auth nowadays...);
  - automatic redirection;
  - proxy support.
* Removed dependency on Netty's internal logging and added SLF4J
* HTTP 1.1 pipelining support
* Requests no longer fail if connection goes down while executing them (they can be returned to the queue so that they can be retried in another connection).
* Added ConcurrentHttpRequestFuture (using Atomics/CAS to avoid synchronized() blocks) 
* Concurrency small fixes & improvements
* Fixed bug where HttpClient.terminate() would hang (in ChannelFactory.releaseExternalResources()) due to rare race condition.

0.7.0
-----
* First release!