@echo off
set SRC=FrontServlet.java
set JAR=FrontServlet.jar
set LOCAL_LIB=..\test\Webapp\WEB-INF\lib
set TOMCAT_WEBAPPS=C:\tomcat\apache-tomcat-10.1.28\webapps
set APP_NAME=Sprint1

rem Crée le dossier lib local si il n'existe pas
if not exist %LOCAL_LIB% mkdir %LOCAL_LIB%

rem Compilation avec la bibliothèque Jakarta
javac -cp "%LOCAL_LIB%\servlet-api.jar" %SRC%

rem Crée la structure de package pour le JAR
if not exist framework mkdir framework
copy FrontServlet.class framework\
jar cvf %LOCAL_LIB%\%JAR% framework\FrontServlet.class

rem Crée la structure de l'application dans Tomcat
if not exist "%TOMCAT_WEBAPPS%\%APP_NAME%" mkdir "%TOMCAT_WEBAPPS%\%APP_NAME%"
if not exist "%TOMCAT_WEBAPPS%\%APP_NAME%\WEB-INF" mkdir "%TOMCAT_WEBAPPS%\%APP_NAME%\WEB-INF"
if not exist "%TOMCAT_WEBAPPS%\%APP_NAME%\WEB-INF\lib" mkdir "%TOMCAT_WEBAPPS%\%APP_NAME%\WEB-INF\lib"

rem Copie le web.xml
copy "..\Test\Webapp\WEB-INF\web.xml" "%TOMCAT_WEBAPPS%\%APP_NAME%\WEB-INF\"

rem Copie le JAR de l'application
copy "%LOCAL_LIB%\%JAR%" "%TOMCAT_WEBAPPS%\%APP_NAME%\WEB-INF\lib\"

rem Copie la bibliothèque Jakarta
copy "%LOCAL_LIB%\servlet-api.jar" "%TOMCAT_WEBAPPS%\%APP_NAME%\WEB-INF\lib\"

rem Nettoyage
del FrontServlet.class
rmdir /s /q framework

echo.
echo Déploiement terminé !
echo.
