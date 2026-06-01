@echo off
cd /d DLRsuite
@echo on 
java -cp ".;lib\*" -Dfile.encoding=UTF-8 GenerateAndTest.GenerateAndRunRandomABoxes "50" "5"
@echo off
cd /d ..