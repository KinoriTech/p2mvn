# p2mvn

```
      ____                                 
 _ __|___ \    /\/\   __ ___   _____ _ __  
| '_ \ __) |  /    \ / _` \ \ / / _ \ '_ \
| |_) / __/  / /\/\ \ (_| |\ V /  __/ | | |
| .__/_____| \/    \/\__,_| \_/ \___|_| |_|
|_|


Welcome to p2 Maven
Downloaded jars and maven scripts (per group id) will be stored in folder: <user.home>\p2mvn
! All the folder contents will be deleted.
----------------------------------------------------------------------------------------------------

If deploying to a public repository, make sure you have the right/permission to publish the p2 content.

----------------------------------------------------------------------------------------------------
? What p2 repository are you exporting to maven

```  

p2mvn is a simple Java application to download jars from a p2 repository and install/deploy them to a maven repository.
The tool downloads the p2 repository jars to the `<user.home>\p2mvn`, grouping them by maven group id, and for each
group id creates either an `install.(bat/sh)` or `deploy.(bat/sh)` that can be executed to install/deploy the downloaded
jars to a maven repository.

**Note** that for public maven repositories, p2 jars are marked as maven "SNAPSHOT" versions by default, and hence you 
can not push them to "maven central"; you will need to push to sonatype or another repository that accepts snapshots. 

