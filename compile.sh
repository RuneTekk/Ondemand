#! /bin/sh
##
# Compile script for Ondemand
#
# Author: Fabian M. <mail.fabianm@gmail.com>
##
echo 'Compiling..'
cd "./src/"
exec javac -cp ./ -d ../bin/build/ org/runetekk/*.java


