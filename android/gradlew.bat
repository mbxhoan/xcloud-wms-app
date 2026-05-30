@ECHO OFF
SET DIRNAME=%~dp0
SET CLASSPATH=%DIRNAME%\gradle\wrapper\gradle-wrapper.jar

IF NOT EXIST "%CLASSPATH%" (
  ECHO Missing gradle wrapper jar at %CLASSPATH%
  EXIT /B 1
)

"%JAVA_HOME%\bin\java.exe" -Xmx64m -Xms64m -Dorg.gradle.appname=gradlew -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
