@echo off
setlocal

REM racine du projet (dossier parent de ce script)
set SCRIPT_DIR=%~dp0
set PROJECT_ROOT=%SCRIPT_DIR%..

REM librairies et cibles
set LOCAL_LIB=%PROJECT_ROOT%\test\webapp\WEB-INF\lib
set TOMCAT_WEBAPPS=C:\tomcat\apache-tomcat-10.1.28\webapps
set APP_NAME=FrontServlet
set JAR=%APP_NAME%.jar

REM créer dossiers si nécessaires
if not exist "%LOCAL_LIB%" mkdir "%LOCAL_LIB%"
if not exist "%PROJECT_ROOT%\build\classes" mkdir "%PROJECT_ROOT%\build\classes"

REM préparer la liste des sources Java
set SOURCES=%TEMP%\sources_%APP_NAME%.txt

pushd "%PROJECT_ROOT%"
dir /b /s *.java > "%SOURCES%"
if not exist "%SOURCES%" (
  echo Aucun fichier Java trouve.
  popd
  exit /b 1
)

REM compiler tous les .java (utilise servlet-api.jar si present)
if exist "%LOCAL_LIB%\servlet-api.jar" (
  javac -cp "%LOCAL_LIB%\servlet-api.jar" -d "%PROJECT_ROOT%\build\classes" @"%SOURCES%"
) else (
  javac -d "%PROJECT_ROOT%\build\classes" @"%SOURCES%"
)

if errorlevel 1 (
  echo Compilation echouee.
  del "%SOURCES%"
  popd
  exit /b 1
)

REM creer le jar de l'application (toutes les classes compilées)
pushd "%PROJECT_ROOT%\build\classes"
jar cvf "%LOCAL_LIB%\%JAR%" .
popd

REM deploy dans Tomcat
if not exist "%TOMCAT_WEBAPPS%\%APP_NAME%" mkdir "%TOMCAT_WEBAPPS%\%APP_NAME%"
if not exist "%TOMCAT_WEBAPPS%\%APP_NAME%\WEB-INF" mkdir "%TOMCAT_WEBAPPS%\%APP_NAME%\WEB-INF"
if not exist "%TOMCAT_WEBAPPS%\%APP_NAME%\WEB-INF\lib" mkdir "%TOMCAT_WEBAPPS%\%APP_NAME%\WEB-INF\lib"
if not exist "%TOMCAT_WEBAPPS%\%APP_NAME%\pages" mkdir "%TOMCAT_WEBAPPS%\%APP_NAME%\pages"

copy "%PROJECT_ROOT%\test\webapp\WEB-INF\web.xml" "%TOMCAT_WEBAPPS%\%APP_NAME%\WEB-INF\" /Y
copy "%LOCAL_LIB%\%JAR%" "%TOMCAT_WEBAPPS%\%APP_NAME%\WEB-INF\lib\" /Y

if exist "%LOCAL_LIB%\servlet-api.jar" (
  copy "%LOCAL_LIB%\servlet-api.jar" "%TOMCAT_WEBAPPS%\%APP_NAME%\WEB-INF\lib\" /Y
)

copy "%PROJECT_ROOT%\test\webapp\pages\*.jsp" "%TOMCAT_WEBAPPS%\%APP_NAME%\pages\" /Y
copy "%PROJECT_ROOT%\test\webapp\pages\*.html" "%TOMCAT_WEBAPPS%\%APP_NAME%\pages\" /Y

REM nettoyage
del "%SOURCES%"

popd
endlocal

echo.
echo Deploiement termine !
echo.
