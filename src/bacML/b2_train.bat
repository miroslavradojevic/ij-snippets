echo off
title something here
:: See the title at the top
python b3_train.py "C:\Users\10250153\bacteria3\Afacealis" 16 5 0.000001 "alexnet_model" 0.0001
REM python b3_train.py "C:\Users\10250153\bacteria3\Afacealis" 32 5 0.0001 "alexnet_model" 0.001
REM python b3_train.py "C:\Users\10250153\bacteria3\Afacealis" 48 5 0.0001 "alexnet_model" 0.001
REM python b3_train.py "C:\Users\10250153\bacteria3\Afacealis" 64 5 0.0001 "alexnet_model" 0.001
echo done