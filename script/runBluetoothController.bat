@echo off
title Bluetooth Controller
java -cp .;lib/bluecove-2.1.1.jar -Djava.library.path=lib/bluecove-2.1.1.jar BluetoothServer

