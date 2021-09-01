# openj9-crash
Reproducer for https://github.com/eclipse-openj9/openj9/issues/13414

Build with `mvn package`

Run with openj9 11 `for i in {1..100}; do java -javaagent:target/openj9-crash-1.0-SNAPSHOT.jar main.Main; done`
