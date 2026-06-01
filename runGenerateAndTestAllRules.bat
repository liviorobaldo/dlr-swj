@echo off
cd /d DLRsuite
@echo on 
java -cp ".;lib\*" -Dfile.encoding=UTF-8 GenerateAndTest.GenerateAndTestAllRules ".\D-KB\UseCaseBaseline.json"
@echo off
cd /d ..