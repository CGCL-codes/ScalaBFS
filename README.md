# ScalaBFS2: A High Performance BFS Accelerator on an HBM-enhanced FPGA Chip

## Introduction

ScalaBFS2 is a high performance BFS accelerator built on an HBM-enhanced FPGA Chip that executes BFS algorithms in a vertex-centered manner. Running at 170\~225 MHz on the Xilinx XCU280 chip, ScalaBFS2 achieves a performance of 56.92 GTEPS (Giga Traversed Edges Per Second), which achieves a speedup of 2.52x\~4.40x compared to state-of-the-art work based on the same chip, and 1.34x\~2.40x compared to Gunrock running on the Nvidia A100 GPU. 

## Organization

- The code for ScalaBFS using Chisel/Verilog language is located in src/ directory. 

- The OpenCL code for the host part is located in host/ directory.

- Vitis project is located in ScalaBFS-proj/ directory after unpacked.

- Graph data processing files are provided in preprocess/ directory.

- Constraints used in the P&R in tcl/ directory.

## Prerequisites

### Hardware

This project works on [Xilinx U280 Data Center Accelerator card](https://www.xilinx.com/products/boards-and-kits/alveo/u280.html).

### Operation System

Ubuntu 18.04 LTS

### Software

[Vitis 2019.2](https://www.xilinx.com/support/download/index.html/content/xilinx/en/downloadNav/vitis/archive-vitis.html)

[U280 Package File on Vitis 2019.2](https://www.xilinx.com/products/boards-and-kits/alveo/u280.html#gettingStarted)

Notice:

1. After the installation of xdma and update the shell on alveo card manually(under normal circumstances , the command is shown in the process of the installtion of xdma. If not , you can use command "/opt/xilinx/xrt/bin/xbmgmt flash --update"), you should cold reboot your machine. The cold reboot means that you should shutdown your machine , unplug the power , wating for several minutes , plug the power and boot up your machine.You can use command 

```
/opt/xilinx/xrt/bin/xbmgmt flash --scan
/opt/xilinx/xrt/bin/xbutil validate
```

to make sure that the runtime enviroment and the alveo card is ready.

2. Don't forget to add the xrt and Vitis to your PATH. Typically you can 

```
source /opt/xilinx/xrt/setup.sh
source /tools/Xilinx/Vitis/2019.2/settings64.sh
```

You can also add this two commands to your .bashrc file.If in the process of making ScalaBFS you fail and see "make: vivado: Command not found", you very likely ignored this step.

### Environment

To compile chisel code, you need to install:

- Java 1.0.8

```
sudo apt install openjdk-8-jre-headless
sudo apt-get install java-wrappers    
sudo apt-get install default-jdk
```

- sbt 1.4.2

```
echo "deb https://dl.bintray.com/sbt/debian /" | \
sudo tee -a /etc/apt/sources.list.d/sbt.list
sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 \
--recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823
sudo apt-get update
sudo apt-get install sbt
```

- Scala 2.11.12

```
sudo apt install scala
```

## Clone and Build 

```
$ git clone https://github.com/CGCL-codes/ScalaBFS/
$ make
```

## Quick Start Guide

### Preprocess

Before deploying and running ScalaBFS, we need to make sure that you have specific graph data with divided csc-csr format that ScalaBFS required.  For complete graph data preprocess guide, see [data_Preprocess/](https://github.com/CGCL-codes/ScalaBFS/tree/master/data_preprocess)

We start with a small directed graph named Wiki-Vote for example. First we should make for directed or undirected graph for propose. Then we generate divided graph data with 32 channels and 64 PEs for ScalaBFS.

```bash
cd data_preprocess
make all
./GraphToScalaBFS Wiki-Vote.txt 32 64 1
```



### Deploy and play

#### Open Vitis & Select workspace:

  ```
  ScalaBFS-proj/
  ```

  Select the "Hardware" target in the left down corner, and press the hammer button to build it! Genarally it will take about 20 hours.

  After generating the bitstream, we can move the generated xclbin file to the bin/ folder and name it according to #PC-#PE:

  ```
  cp ScalaBFS-proj/FinalBFS_32/Hardware/binary_container_1.xclbin bin/32-128.bin
  ```

#### Choose graph data (modify pc32.cpp in host/)

For the preprocessed wiki-vote graph data mentioned before, we should first add the input file configuration as line 144~153:

  The configuration concludes 
  - graph name
  - graph file directory : 'bfs_filename'
  - the edge count of the graph: 'result'
  - the other parameter like 'csr_c_addr' which can be obtained from data_preprocess/Wiki-Vote_addr_pe_128_ch_32.log:

  ```
  if(graph == "wiki_vote"){
      bfs_filename = "data_preprocess/Wiki-Vote_pe_128_ch_"; // your graph file directory
      result = 103689;
      csr_c_addr = 260;
      csr_r_addr = 0;
      level_addr = 1837;
      node_num = 8298;
      csc_c_addr = 1227;
      csc_r_addr = 967;
  }
  ```


After that, it's time to execute the program, we can specify the root node used when executing the BFS algorithm and the switch position between push mode and pull mode:

```
make update_obj pc=32
make run pc=32 pe=128 graph=wiki_vote root=3 push2pull=3 pull2push=5
```


## Graph datasets

TABLE 1: Graph datasets

| **Graphs**               | **Directed** | **\#Vertices (M)** | **\#Edges (M)** | **Average Degree** | **Pre\-processing time (s)** | **Edge\-data Expansion Rate** |
|:------------------------:|:------------:|:--------------:|:-----------:|:-----------:|:------------------------:|:------------------------:|
| soc\-Pokec \(PK\)        | Y            | 1\.63          | 30\.62      | 18\.75      | 3\.547                   | 1\.19                    |
| soc\-LiveJournal \(LJ\)  | Y            | 4\.85          | 68\.99      | 14\.23      | 8\.775                   | 1\.28                    |
| com\-Orkut \(OR\)        | N            | 3\.07          | 234\.37     | 76\.28      | 17\.544                  | 1\.05                    |
| hollywood\-2009 \(HO\)   | N            | 1\.14          | 113\.89     | 99\.91      | 7\.601                   | 1\.04                    |
| web\-hudong \(HD\)       | Y            | 1\.98          | 14\.87      | 7\.49       | 2\.246                   | 1\.48                    |
| web\-baidu\-baike \(BB\) | Y            | 2\.14          | 17\.80      | 8\.31       | 2\.438                   | 1\.44                    |
| RMAT18\-8 \(R18\-8\)     | N            | 0\.26          | 2\.05       | 7\.81       | 0\.339                   | 1\.30                    |
| RMAT18\-16 \(R18\-16\)   | N            | 0\.26          | 4\.03       | 15\.39      | 0\.494                   | 1\.18                    |
| RMAT18\-32 \(R18\-32\)   | N            | 0\.26          | 7\.88       | 30\.06      | 0\.727                   | 1\.10                    |
| RMAT18\-64 \(R18\-64\)   | N            | 0\.26          | 15\.22      | 58\.07      | 1\.129                   | 1\.06                    |
| RMAT22\-16 \(R22\-16\)   | N            | 4\.19          | 65\.97      | 15\.73      | 6\.602                   | 1\.15                    |
| RMAT22\-32 \(R22\-32\)   | N            | 4\.19          | 130\.49     | 31\.11      | 10\.209                  | 1\.09                    |
| RMAT22\-64 \(R22\-64\)   | N            | 4\.19          | 256\.62     | 61\.18      | 17\.818                  | 1\.05                    |
| RMAT23\-64 \(R23\-64\)   | N            | 8\.39          | 517\.34     | 61\.67      | 35\.951                  | 1\.05                    |


