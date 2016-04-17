Copy Artifact Plugin
====================

See https://wiki.jenkins-ci.org/display/JENKINS/Copy+Artifact+Plugin

Notes for development
---------------------

* When running tests with Java < 8
    * You will see `OutOfMemoryError: PermGen space.` when running tests.
    * set `set MAVEN_OPTS=-XX:MaxPermSize=128m` to avoid that error.
    * You never see this issue with Java 8, as Java 8 no longer have PermGen spaces.
    
