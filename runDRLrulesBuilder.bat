@echo off
cd /d DLRsuite
@echo on 
java -cp ".;lib\*" -Dfile.encoding=UTF-8 DKBmanager.DRLrulesBuilder
@echo off
cd /d ..