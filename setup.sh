#! /bin/sh
##
# Setup script for Ondemand
#
# Author: Fabian M. <mail.fabianm@gmail.com>
##
exec java -cp ./bin/build/ org.runetekk.Main setup etc/server.properties
