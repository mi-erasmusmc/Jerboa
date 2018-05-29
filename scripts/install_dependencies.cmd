
call mvn install:install-file -Dfile="${project.basedir}/lib/commons-beanutils-1.8.3.jar" -DgroupId=libraries -DartifactId=commons-beanutils -Dversion=1.8.3 -Dpackaging=jar 
call mvn install:install-file -Dfile="${project.basedir}/lib/commons-commons-codec-1.10.jar" -DgroupId=libraries -DartifactId=commons-codec -Dversion=1.10 -Dpackaging=jar 
call mvn install:install-file -Dfile="${project.basedir}/lib/commons-collections-3.2.1.jar" -DgroupId=libraries -DartifactId=commons-collections -Dversion=3.2.1 -Dpackaging=jar 
call mvn install:install-file -Dfile="${project.basedir}/lib/commons-io-2.4.jar" -DgroupId=libraries -DartifactId=commons-io -Dversion=2.4 -Dpackaging=jar 
call mvn install:install-file -Dfile="${project.basedir}/lib/commons-lang3-3.1.jar" -DgroupId=libraries -DartifactId=commons-lang3 -Dversion=3.1 -Dpackaging=jar 
call mvn install:install-file -Dfile="${project.basedir}/lib/commons-logging-1.1.3.jar" -DgroupId=libraries -DartifactId=commons-logging-1 -Dversion=1.3 -Dpackaging=jar 
call mvn install:install-file -Dfile="${project.basedir}/lib/commons-math3-3.2.jar" -DgroupId=libraries -DartifactId=commons-math3 -Dversion=3.2 -Dpackaging=jar 
call mvn install:install-file -Dfile="${project.basedir}/lib/iText-2.1.5.jar" -DgroupId=libraries -DartifactId=iText -Dversion=2.1.5 -Dpackaging=jar 
call mvn install:install-file -Dfile="${project.basedir}/lib/jcommon-1.0.16.jar" -DgroupId=libraries -DartifactId=jcommon -Dversion=1.0.16 -Dpackaging=jar 
call mvn install:install-file -Dfile="${project.basedir}/lib/jfreechart-1.0.19.jar" -DgroupId=libraries -DartifactId=jfreechart -Dversion=1.0.19 -Dpackaging=jar 
call mvn install:install-file -Dfile="${project.basedir}/lib/mvel2-2.2.0.Final.jar" -DgroupId=libraries -DartifactId=mvel2 -Dversion=2.2.0 -Dpackaging=jar 
call mvn install:install-file -Dfile="${project.basedir}/lib/mysql-connector-java-5.1.28-bin.jar" -DgroupId=libraries -DartifactId=mysqlconnector -Dversion=5.1.28 -Dpackaging=jar 
call mvn install:install-file -Dfile="${project.basedir}/lib/postgresql-9.3-1102.jdbc41.jar" -DgroupId=libraries -DartifactId=postgresql -Dversion=9.3 -Dpackaging=jar 

