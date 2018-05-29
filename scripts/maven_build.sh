mvn install:install-file -q -Dfile="./lib/commons-lang3-3.1.jar" -DgroupId=commons-lang3 -DartifactId=commons-lang3 -Dversion=3.1 -Dpackaging=jar
mvn install:install-file -q -Dfile="./lib/commons-logging-1.1.3.jar" -DgroupId=commons-logging-1 -DartifactId=commons-logging-1 -Dversion=1.3 -Dpackaging=jar
mvn install:install-file -q -Dfile="./lib/commons-math3-3.2.jar" -DgroupId=commons-math3 -DartifactId=commons-math3 -Dversion=3.2 -Dpackaging=jar
mvn install:install-file -q -Dfile="./lib/iText-2.1.5.jar" -DgroupId=iText -DartifactId=iText -Dversion=2.1.5 -Dpackaging=jar
mvn install:install-file -q -Dfile="./lib/jcommon-1.0.16.jar" -DgroupId=jcommon -DartifactId=jcommon -Dversion=1.0.16 -Dpackaging=jar
mvn install:install-file -q -Dfile="./lib/jfreechart-1.0.19.jar" -DgroupId=jfreechart -DartifactId=jfreechart -Dversion=1.0.19 -Dpackaging=jar
mvn install:install-file -q -Dfile="./lib/mvel2-2.2.0.Final.jar" -DgroupId=mvel2 -DartifactId=mvel2 -Dversion=2.2.0 -Dpackaging=jar
mvn install:install-file -q -Dfile="./lib/mysql-connector-java-5.1.28-bin.jar" -DgroupId=mysqlconnector -DartifactId=mysqlconnector -Dversion=5.1.28 -Dpackaging=jar
mvn install:install-file -q -Dfile="./lib/postgresql-9.3-1102.jdbc41.jar" -DgroupId=postgresql -DartifactId=postgresql -Dversion=9.3 -Dpackaging=jar
mvn install verify assembly:single
