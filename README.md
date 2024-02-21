# ScalaBFS2: A High Performance BFS Accelerator on an HBM-enhanced FPGA Chip

***This is the latest version of ScalaBFS2 (the same as [ScalaBFS_v2 branch](https://github.com/CGCL-codes/ScalaBFS/tree/ScalaBFS_v2)), the historical version of ScalaBFS is under the [ScalaBFS_v1 branch](https://github.com/CGCL-codes/ScalaBFS/tree/ScalaBFS_v1).***

## Introduction

ScalaBFS2 is a high performance BFS accelerator built on an HBM-enhanced FPGA Chip that executes BFS algorithms in a **vertex-centered** manner. Running at 170\~225 MHz on the Xilinx XCU280 chip, ScalaBFS2 achieves a performance of **56.92 GTEPS** (Giga Traversed Edges Per Second).

## Organization

- The code for ScalaBFS using Chisel/Verilog language is located in [src/](https://github.com/CGCL-codes/ScalaBFS/tree/master/src/main/scala) directory. 

- The OpenCL code for the host part is located in [host/](https://github.com/CGCL-codes/ScalaBFS/tree/master/host) directory.

- Vitis project is located in ScalaBFS-proj/ directory after unpacked.

- Graph data processing files are provided in [data_preprocess/](https://github.com/CGCL-codes/ScalaBFS/tree/master/data_preprocess) directory.

- Constraints used in the P&R in [tcl/](https://github.com/CGCL-codes/ScalaBFS/tree/master/tcl) directory.

## Prerequisites

### Hardware

This project works on [Xilinx U280 Data Center Accelerator card](https://www.xilinx.com/products/boards-and-kits/alveo/u280.html).

### Operation System

Ubuntu 18.04 LTS

### Software

[Vitis 2019.2](https://www.xilinx.com/support/download/index.html/content/xilinx/en/downloadNav/vitis/archive-vitis.html) 

\* ScalaBFS2 is compatible with later versions of Vitis (above 2019.2), but we recommend using a smaller scale or reducing the frequency when trying other versions. This is because the constraint files in the tcl/ directory are currently optimized for ScalaBFS2 to run in the 2019.2 version of Vitis and may need adjustments for other versions.

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

```bash
git clone https://github.com/CGCL-codes/ScalaBFS/
vim src/main/scala/configuration.scala # modify the Configurations
make
```

The Configurations conclude the number of PCs, the number of PEs per PC, and the distribution of PGs within the three SLRs. The largest scale of ScalaBFS2 on U280 is 128-PE/32-PC (i.e., channel_num = 32, pipe_num_per_channel = 4), and **the P&R process will take approximately 20 hours**.

```scala
//***************** Configurations ************************************
val channel_num = 32            // the number of PCs
val pipe_num_per_channel = 4    // the number of PEs per PC

val shift_channel = 5           // Log2(channel_num) //
val shift_pipe = 2              // Log2(pipe_num_per_channel)

val slr0_channel_num = 0        // the number of PGs placed in SLR0, recommended as 0
val slr1_channel_num = 14       // the number of PGs placed in SLR1
val slr2_channel_num = channel_num - slr0_channel_num - slr1_channel_num    // the number of PGs placed in SLR2

//*********************************************************************
```


## Quick Start Guide

### Preprocess

Before deploying and running ScalaBFS, we need to make sure that you have specific graph data with divided csc-csr format that ScalaBFS required.  For complete graph data preprocess guide, see [data_preprocess/](https://github.com/CGCL-codes/ScalaBFS/tree/master/data_preprocess)

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
| [soc\-Pokec \(PK\)](https://snap.stanford.edu/data/soc-Pokec.html)                | Y            | 1\.63          | 30\.62      | 18\.75      | 3\.547                   | 1\.19                    |
| [soc\-LiveJournal \(LJ\)](https://snap.stanford.edu/data/soc-LiveJournal1.html)   | Y            | 4\.85          | 68\.99      | 14\.23      | 8\.775                   | 1\.28                    |
| [com\-Orkut \(OR\)](https://snap.stanford.edu/data/com-Orkut.html)                | N            | 3\.07          | 234\.37     | 76\.28      | 17\.544                  | 1\.05                    |
| [hollywood\-2009 \(HO\)](https://networkrepository.com/ca-hollywood-2009.php)     | N            | 1\.14          | 113\.89     | 99\.91      | 7\.601                   | 1\.04                    |
| [web\-hudong \(HD\)](https://networkrepository.com/web-hudong.php)                | Y            | 1\.98          | 14\.87      | 7\.49       | 2\.246                   | 1\.48                    |
| [web\-baidu\-baike \(BB\)](https://networkrepository.com/web-baidu-baike.php)     | Y            | 2\.14          | 17\.80      | 8\.31       | 2\.438                   | 1\.44                    |
| [wiki\-Talk \(WT\)](https://snap.stanford.edu/data/wiki-Talk.html)                | Y            | 2\.39          | 5\.02       | 2\.10       | 4\.380                   | 1\.17                    |
| [com\-Youtube \(YT\)](https://snap.stanford.edu/data/com-Youtube.html)            | Y            | 1\.13          | 5\.98       | 5\.27       | 3\.423                   | 2\.11                    |
| RMAT18\-8 \(R18\-8\)     | N            | 0\.26          | 2\.05       | 7\.81       | 0\.339                   | 1\.30                    |
| RMAT18\-16 \(R18\-16\)   | N            | 0\.26          | 4\.03       | 15\.39      | 0\.494                   | 1\.18                    |
| RMAT18\-32 \(R18\-32\)   | N            | 0\.26          | 7\.88       | 30\.06      | 0\.727                   | 1\.10                    |
| RMAT18\-64 \(R18\-64\)   | N            | 0\.26          | 15\.22      | 58\.07      | 1\.129                   | 1\.06                    |
| RMAT22\-16 \(R22\-16\)   | N            | 4\.19          | 65\.97      | 15\.73      | 6\.602                   | 1\.15                    |
| RMAT22\-32 \(R22\-32\)   | N            | 4\.19          | 130\.49     | 31\.11      | 10\.209                  | 1\.09                    |
| RMAT22\-64 \(R22\-64\)   | N            | 4\.19          | 256\.62     | 61\.18      | 17\.818                  | 1\.05                    |
| RMAT23\-64 \(R23\-64\)   | N            | 8\.39          | 517\.34     | 61\.67      | 35\.951                  | 1\.05                    |


