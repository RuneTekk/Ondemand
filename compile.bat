@echo off
echo 'Compiling...'
cd ./src/
javac -cp ./ -d ../bin/build/ org/runetekk/*.java
pause