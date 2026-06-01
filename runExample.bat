@echo off
cd /d DLRsuite
@echo on 
java -cp ".;lib\*" -Dfile.encoding=UTF-8 RunExample ".\Examples\Example1.ttl"
@echo off
cd /d ..