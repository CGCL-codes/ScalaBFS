# ScalaBFS: A Scalable BFS Accelerator on FPGA-HBM Platform

ScalaBFS is an BFS accelerator built on top of an FPGA configured with HBM (i.e., FPGA-HBM platform) which can scale its performance according to the available memory channels (on a single card). It utlizes multiple processing elements to sufficiently exploit the high bandwidth of HBM to improve efficiency. We implement the prototype system of ScalaBFS on Xilinx Alveo U280 FPGA card (real hardware).

## Organization

The code for ScalaBFS using Chisel language is located in src/ directory. Vitis project is located in ScalaBFS-proj/ directory after unpacked. Graph data processing files are provided in preprocess/ directory.

## Prerequisites

### Hardware

This project works on [Xilinx U280 Data Center Accelerator card](https://www.xilinx.com/products/boards-and-kits/alveo/u280.html).

### Operation System

Ubuntu 18.04 LTS

### Software

[Vitis 2019.2](https://www.xilinx.com/support/download/index.html/content/xilinx/en/downloadNav/vitis/2019-2.html)

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

3. If you meet "PYOPENCL INSTALL FAILED" in the installtion of xrt , refer to [AR# 73055](https://www.xilinx.com/support/answers/73055.html)

4. If you meet "XRT Requires opencl header" when you open Vitis , refer to [Vitis prompt “XRT Requires opencl header"](https://forums.xilinx.com/t5/Vitis-Acceleration-SDAccel-SDSoC/Vitis-prompt-XRT-Requires-opencl-header-quot/td-p/1087072)
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
$ git clone https://github.com/lizardll/ScalaBFS.git
$ make
```

## Quick Start Guide

### Preprocess

Before deploying and running ScalaBFS, we need to make sure that you have specific graph data with divided csc-csr format that ScalaBFS required.  For complete graph data preprocess guide, see [Data Preprocess.](https://github.com/lizardll/ScalaBFS/tree/master/data_preprocess)

We start with a small directed graph named Wiki-Vote for example. First we should make for directed or undirected graph for propose. Then we generate divided graph data with 32 channels and 64 PEs for ScalaBFS.

```bash
cd data_preprocess
make all
./GraphToScalaBFS Wiki-Vote.txt 32 64
```



### Deploy and play

#### Open Vitis & Select workspace:

  ```
  ScalaBFS-proj/workspace
  ```

#### Choose graph data (modify host_example.cpp in vitis)

For the preprocessed wiki-vote graph data mentioned before, we should first modify the input file name at line 121:

  ```
  string bfs_filename = "YOUR_DIR_HERE/ScalaBFS/data_preprocess/Wiki-Vote_pe_64_ch_";
  ```

Then we have to modify the following line 122-127 according to data_preprocess/Wiki-Vote_addr_pe_64_ch_32.log:

  ```
    cl_uint csr_c_addr = 260;
    cl_uint csr_r_addr = 0;
    cl_uint level_addr = 2958;
    cl_uint node_num = 8298;
    cl_uint csc_c_addr = 1780;
    cl_uint csc_r_addr = 1520;
  ```
  
And in order to show correct prerformance value, on line 132 we also need to set the edge count of the dataset (in this case, wiki-vote has 103689 edges):
  ```
  result = 103689;
  ```
After that, it's time to build the whole project in vitis. Select the "Hardware" target in the left down corner, and press the hammer button to build it! Genarally it will take 10~15 hours.

The running results will be like this:

<img src="https://github.com/lizardll/ScalaBFS/blob/master/docs/screenshot.png" width="400">

## Experiment results

TABLE 1: Graph datasets

<table>
<thead>
  <tr>
    <th rowspan="2">Graphs</th>
    <th>Vertices</th>
    <th>Edges</th>
    <th>Avg.</th>
    <th rowspan="2">Directed</th>
  </tr>
  <tr>
    <td>(M)</td>
    <td>(M)</td>
    <td>Degree</td>
  </tr>
</thead>
<tbody>
  <tr>
    <td>soc-Pokec (PK)</td>
    <td>1.63</td>
    <td>30.62</td>
    <td>18.75</td>
    <td>Y</td>
  </tr>
  <tr>
    <td>soc-LiveJournal (LJ)</td>
    <td>4.85</td>
    <td>68.99</td>
    <td>14.23</td>
    <td>Y</td>
  </tr>
  <tr>
    <td>com-Orkut (OR)</td>
    <td>3.07</td>
    <td>234.37</td>
    <td>76.28</td>
    <td>N</td>
  </tr>
  <tr>
    <td>hollywood-2009 (HO)</td>
    <td>1.14</td>
    <td>113.89</td>
    <td>99.91</td>
    <td>N</td>
  </tr>
  <tr>
    <td>RMAT18-8</td>
    <td>0.26</td>
    <td>2.05</td>
    <td>7.81</td>
    <td>N</td>
  </tr>
  <tr>
    <td>RMAT18-16</td>
    <td>0.26</td>
    <td>4.03</td>
    <td>15.39</td>
    <td>N</td>
  </tr>
  <tr>
    <td>RMAT18-32</td>
    <td>0.26</td>
    <td>7.88</td>
    <td>30.06</td>
    <td>N</td>
  </tr>
  <tr>
    <td>RMAT18-64</td>
    <td>0.26</td>
    <td>15.22</td>
    <td>58.07</td>
    <td>N</td>
  </tr>
  <tr>
    <td>RMAT22-16</td>
    <td>4.19</td>
    <td>65.97</td>
    <td>15.73</td>
    <td>N</td>
  </tr>
  <tr>
    <td>RMAT22-32</td>
    <td>4.19</td>
    <td>130.49</td>
    <td>31.11</td>
    <td>N</td>
  </tr>
  <tr>
    <td>RMAT22-64</td>
    <td>4.19</td>
    <td>256.62</td>
    <td>61.18</td>
    <td>N</td>
  </tr>
  <tr>
    <td>RMAT23-16</td>
    <td>8.39</td>
    <td>132.38</td>
    <td>15.78</td>
    <td>N</td>
  </tr>
  <tr>
    <td>RMAT23-32</td>
    <td>8.39</td>
    <td>262.33</td>
    <td>31.27</td>
    <td>N</td>
  </tr>
  <tr>
    <td>RMAT23-64</td>
    <td>8.39</td>
    <td>517.34</td>
    <td>61.67</td>
    <td>N</td>
  </tr>
</tbody>
</table>

TABLE 2: Performance comparison between GunRock and ScalaBFS (32-PC/64-PE configuration)

<table>
<thead>
  <tr>
    <th></th>
    <th colspan="2">Gunrock on V100</th>
    <th colspan="2">ScalaBFS on U280</th>
  </tr>
</thead>
<tbody>
  <tr>
    <td>Datasets</td>
    <td>Throughput<br>(GTEPS)</td>
    <td>Power eff.<br>(GTEPS/watt)</td>
    <td>Throughput<br>(GTEPS)</td>
    <td>Power eff.<br>(GTEPS/watt)</td>
  </tr>
  <tr>
    <td>soc-Pokec (PK)</td>
    <td>14.9</td>
    <td>0.050</td>
    <td>22.44</td>
    <td>0.455</td>
  </tr>
  <tr>
    <td>soc-LiveJournal (LJ)</td>
    <td>18.5</td>
    <td>0.062</td>
    <td>15.62</td>
    <td>0.317</td>
  </tr>
  <tr>
    <td>com-Orkut (OR)</td>
    <td>150.6</td>
    <td>0.502</td>
    <td>26.22</td>
    <td>0.532</td>
  </tr>
  <tr>
    <td>hollywood-2009 (HO)</td>
    <td>73</td>
    <td>0.243</td>
    <td>26.22</td>
    <td>0.455</td>
  </tr>
</tbody>
</table>

FIGURE 1: Performances and aggregated bandwidths of ScalaBFS (with 32 HBM PCs and 64 PEs) and baseline case

<img src="docs/compare-naive.jpg" width="600">
