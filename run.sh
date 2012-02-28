#! /bin/sh
##
# Run script for Ondemand
#
# Author: Fabian M. <mail.fabianm@gmail.com>
##
exec java -cp ./bin/build/ org.runetekk.Main server etc/server.properties
