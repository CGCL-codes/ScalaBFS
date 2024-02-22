#! /usr/bin/python  

# To fix the error of missing initial lastGrant that occurs when Chisel code is compiled to Verilog

fp = open("Top.v", "r")
findmodule = 0 
findmodule2 = 0
findmodule3 = 0
findinit = 0
find4 = 0
find5 = 0
find = 0
modulename = "module RRArbiter"   
modulename2= "module sub_crossbar"   

init = "initial begin"   
cont = "\tlastGrant = 1'b0;"
f = fp.read()
a = f.split('\n')
i = 0
count = 0
for s in a:
    i = i + 1
    findmodule = s.find(modulename)
    findmodule2 = s.find(modulename2)
    if findmodule >= 0:
        print(s)
        find = find + 1
    if find > 0:
        findinit = s.find(init)
        if(findinit >= 0):
            a.insert(i, cont)
            print("write")
            find = 0
f = '\n'.join(a)
fp = open("Top.v", "w")
fp.write(f)
fp.close()
print(count)