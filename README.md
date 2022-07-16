# Java-Memory-Guardian
Checks memory usage of your app and creates heap-dumps automatically.
Useful when the JVM fails to create heap-dumps on out of memory errors.
## Usage
- Download the jar from the latest release.
- Put it inside a directory, open a terminal there and execute 
`java -jar jmg.jar jdk-dir <path> heap-dir <path> jar-name <name> max-mb <value>`