@echo off
rem Copyright 2010 Google Inc.
rem
rem Licensed under the Apache License, Version 2.0 (the "License");
rem you may not use this file except in compliance with the License.
rem You may obtain a copy of the License at
rem
rem      http://www.apache.org/licenses/LICENSE-2.0
rem
rem Unless required by applicable law or agreed to in writing, software
rem distributed under the License is distributed on an "AS IS" BASIS,
rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
rem See the License for the specific language governing permissions and
rem limitations under the License.

set BASEDIR=%~dp0
call :which android.bat

rem Use tools in Android SDK to figure out the swt.jar for the current JVM.
for /f %%a in ('java -jar "%LIBDIR%\archquery.jar"') do set SWTDIR=%LIBDIR%\%%a

rem Ensure that adb server is running
adb.exe start-server

rem Start Android Projector
call java -Djava.ext.dirs="%LIBDIR%;%SWTDIR%" -jar "%BASEDIR%AndroidProjector.jar"

goto :EOF

:which
set TOOLSDIR=%~dp$PATH:1
set LIBDIR=%TOOLSDIR%lib
