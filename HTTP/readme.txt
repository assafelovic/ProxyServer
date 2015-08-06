Multi-Threaded HTTP Web Server, ver 1.0, Dec, 26. 2014.

Written and coded by Assaf Elovic
Extract in C:\serverroot



WebServer.java Class:

The class contains the methods:

1. ExtractConfig:
Parses the config.ini file parameters into a hash map.
this parameters are then extracted and initialised (at main) into the global variables of the class for later use.

2. main:
At first the values from config.ini are extracted with the use of the method (1).

Then a socket is opened and waits for incoming clients.
When a client is accepted, a new thread is started (with HttpRequest.run()) under the condition that the number of threads < maxThreads (init to 10).
If they’re 10 threads running, the current request will be infinitely looped until there is a an open slot.



HttpRequest.java Class:

Abstract:
This class accepts a thread for http requests and processing handling.

Processing the request contains a few steps:
Step 1 - Read the request through InputStream (and into a buffer) and save into a string.

Step 2 - Print the request header console.

Step 3 - Analyse the request (GET/POST/TRACE/OPTIONS ??).

Step 4 - If request is non of the above -> sends 501 Not Implemented code to client, else, proceed to step 5.

Step 5 - Foreach request to be considered, if eligible (one of requests from step 3), process the request which contains 2 stages:

A. print the request status (if OK - 200, else 404) and send status to client through DataOutputStream (write method).

B. if 200 OK, write file bytes to client (depending if method is POST/GET or HEAD/TRACE/OPTIONS).



Assumptions to be noticed:

1. OPTIONS -> Returns a header of allowed options only to client.

2. HEAD -> Returns headers of status only, without writing content, to client.

3. TRACE -> Returns headers of request to client.

4. GET -> Returns headers of status to client, and if files exists, writes contents to client.

5. POST -> Same as GET, however params are taken from body and not request header.



Notes to be noticed:
1. As stated and requested in the Lab 1 exercise, if client requests params_info.html (for example: localhost:8080/params_info.html), our implementation consists of:
Depending on POST/GET methods, extract parameters and values from body/header of request and save into a hash table -> create an html file with a table consisting vars/params into the root directory -> Reade it through a regular GET/POST method.

2. The functionality of the HttpRequest Class is so that the user cannot reach files/directories outside the root file (for example: passwords..) by adding the root path foreach file which is requested by the server. If the user addresses a different file/directory, a simple 404 will be returned.
3. A favicon.ico has been added to the index.html file and root directory.