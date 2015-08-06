Multi-Threaded Content-Filtering Proxy Server, ver 1.0, February 14 2015.
Written and coded by Assaf Elovic.

- It is using thread pool, so after closing connection between browser and proxy, threads not stopping, they are caching and waiting for next connection, so this program uses less memory.
- If you set maxThreads value in config.ini to 0, it will not limit count of threads.
- If error occurs, thread will close current connection and continue working, if thread will get fatal error and stop working, server will remove it from cache, and print notification to console (while we were testing it, we've got no fallen threads).
- On page content-proxy/logs it prints all blocked connections and then count of working and cached threads.
- On page content-proxy/policies you can see content of policies file, and after changing information, you have to press "submit" button at the top of page. Browser will send POST request to content-proxy/policies/change and server will change policies file if everything went well, or print stack trace if error occurred.
- All messages, that are writing to console are: First line of header for each request; "Connection closed" when browser disconnects; "Block <time> | <firstLineOfHeader>" if proxy blocks connection.
- HTTP folder is our Web Server from Lab 1. If you remove run.config, it will ignore the Web Server and will work just like a regular proxy.
- Notice: there is no serverroot folder. You can extract this .zip wherever you want and it will work. The non-code file of the website are under HTTP\Website.