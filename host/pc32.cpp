/**********
Copyright (c) 2019, Xilinx, Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice,
this list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its contributors
may be used to endorse or promote products derived from this software
without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
**********/

/********************************************************************************************
 * Description:
 *
 * Xilinx's High Bandwidth Memory (HBM)-enabled FPGA are the clear solution for providing
 * massive memory bandwidth within the lowest power, footprint, and system cost envolopes.
 *
 * This example is designed to show a simple use case to understand how to use HBM memory.
 *
 * There are total 32 memory resources referenced as HBM[0:31] by V++ and each has a
 * capacity of storing 256MB of data.
 *
 * This example showcases two use cases to differentiate how efficiently one can use HBM banks.
 *
 * CASE 1:
 *          +-----------+                   +-----------+
 *          |           | ---- Input1 ----> |           |
 *          |           |                   |           |
 *          |   HBM0    | ---- Input2 ----> |   KERNEL  |
 *          |           |                   |           |
 *          |           | <---- Output ---- |           |
 *          +-----------+                   +-----------+
 *
 *  In this case only one HBM Bank, i.e. HBM0, has been used for both the input
 *  vectors and the processed output vector.
 *
 *  CASE 2:
 *          +-----------+                   +-----------+
 *          |           |                   |           |
 *          |   HBM1    | ---- Input1 ----> |           |
 *          |           |                   |           |
 *          +-----------+                   |           |
 *                                          |           |
 *          +-----------+                   |           |
 *          |           |                   |   KERNEL  |
 *          |   HBM2    | ---- Input2 ----> |           |
 *          |           |                   |           |
 *          +-----------+                   |           |
 *                                          |           |
 *          +-----------+                   |           |
 *          |           |                   |           |
 *          |   HBM3    | <---- Output ---- |           |
 *          |           |                   |           |
 *          +-----------+                   +-----------+
 *
 *  In this case three different HBM Banks, i.e. HBM1, HBM2 and HBM3, have been used for input
 *  vectors and the processed output vector.
 *  The banks HBM1 & HBM2 are used for input vectors whereas HBM3 is used for
 *  processed output vector.
 *
 *  The use case highlights significant change in the data transfer throughput (in terms of
 *  Gigabytes per second) when a single and multiple HBM banks are used for the
 *  same application.
 *
 *  *****************************************************************************************/

#include "xcl2.hpp"
#include <algorithm>
#include <iostream>
#include <stdint.h>
#include <stdlib.h>
#include <string>
#include <vector>
using namespace std;
//Number of HBM Banks required
#define MAX_HBM_BANKCOUNT 32
#define BANK_NAME(n) n | XCL_MEM_TOPOLOGY
const int bank[MAX_HBM_BANKCOUNT] = {
    BANK_NAME(0),  BANK_NAME(1),  BANK_NAME(2),  BANK_NAME(3),  BANK_NAME(4),
    BANK_NAME(5),  BANK_NAME(6),  BANK_NAME(7),  BANK_NAME(8),  BANK_NAME(9),
    BANK_NAME(10), BANK_NAME(11), BANK_NAME(12), BANK_NAME(13), BANK_NAME(14),
    BANK_NAME(15), BANK_NAME(16), BANK_NAME(17), BANK_NAME(18), BANK_NAME(19),
    BANK_NAME(20), BANK_NAME(21), BANK_NAME(22), BANK_NAME(23), BANK_NAME(24),
    BANK_NAME(25), BANK_NAME(26), BANK_NAME(27), BANK_NAME(28), BANK_NAME(29),
    BANK_NAME(30), BANK_NAME(31)};




int main(int argc, char *argv[]) {
    if (argc != 6) {
        printf("Usage: %s <XCLBIN> \n", argv[0]);
        return -1;
    }
    cl_int err;
    cl::Context context;
    cl::CommandQueue q;
    cl::Kernel kernel_bfs;
    std::string binaryFile = argv[1];

    // The get_xil_devices will return vector of Xilinx Devices
    auto devices = xcl::get_xil_devices();


    std::string graph = argv[2];

    string bfs_filename;
    cl_uint csr_c_addr;
    cl_uint csr_r_addr;
    cl_uint level_addr;
    cl_uint node_num;
    cl_uint csc_c_addr;
    cl_uint csc_r_addr;

    cl_uint root = atoi(argv[3]);
    cl_uint pipe_num = 32;
    cl_uint ali_num = 8;
    cl_uint push_to_pull_level = atoi(argv[4]);
    cl_uint pull_to_push_level = atoi(argv[5]);
    double result = 0;
    double fre = 0; 
    fre = 170; //frequency
    unsigned int dataSize = 32*1024*1024;
    FILE *fr;

    if(graph == "wiki_vote"){
        bfs_filename = "data_preprocess/Wiki-Vote_pe_128_ch_"; // your graph file directory
        csr_c_addr = 260;
        csr_r_addr = 0;
        level_addr = 1837;
        node_num = 8298;
        csc_c_addr = 1227;
        csc_r_addr = 967;
        result = 103689;
    }

    
    // read_binary_file() command will find the OpenCL binary file created using the
    // V++ compiler load into OpenCL Binary and return pointer to file buffer.
    auto fileBuf = xcl::read_binary_file(binaryFile);

    cl::Program::Binaries bins{{fileBuf.data(), fileBuf.size()}};
    int valid_device = 0;
    for (unsigned int i = 0; i < devices.size(); i++) {
        auto device = devices[i];
        // Creating Context and Command Queue for selected Device
        OCL_CHECK(err, context = cl::Context(device, NULL, NULL, NULL, &err));
        OCL_CHECK(err,
                  q = cl::CommandQueue(
                      context, device, CL_QUEUE_PROFILING_ENABLE, &err));

        std::cout << "Trying to program device[" << i
                  << "]: " << device.getInfo<CL_DEVICE_NAME>() << std::endl;
        cl::Program program(context, {device}, bins, NULL, &err);
        if (err != CL_SUCCESS) {
            std::cout << "Failed to program device[" << i
                      << "] with xclbin file!\n";
        } else {
            std::cout << err<<"Device[" << i << "]: program successful!\n";
            OCL_CHECK(err,
                      kernel_bfs = cl::Kernel(program, "FinalBFS_32", &err));
            valid_device++;
            std::cout <<err<< "Device[" << i << "]: kernel_bfs successful!\n";
            break; // we break because we found a valid device
        }
    }
    if (valid_device == 0) {
        std::cout << "Failed to program any device found, exit!\n";
        exit(EXIT_FAILURE);
    }
    std::cout << "program successful!\n";

    std::vector<cl_long,aligned_allocator<cl_long>> bfs_data[pipe_num];
    std::vector<cl_long,aligned_allocator<cl_long>> bfs_tmp_data[pipe_num];
    for(cl_uint pipe_i = 0; pipe_i < pipe_num; pipe_i++){
      bfs_data[pipe_i].resize(dataSize);
      bfs_tmp_data[pipe_i].resize(dataSize);
    }

    cl_long bfs_data_size = 0;

    string bfs_data_name;
    std::vector <FILE *> fin(pipe_num);
    for(cl_uint i = 0;i<pipe_num;i++){
    	bfs_data_name = bfs_filename + to_string(pipe_num) + "_" +to_string(i) +".bin";
        std::cout << "*pipe "<<i<<" "<<bfs_data_name<<endl;
    	fin[i] = fopen(bfs_data_name.c_str(),"rb");
    	fseek(fin[i], 0, SEEK_END);
    	bfs_data_size = ftell(fin[i]) / 8;
    	fseek(fin[i], 0, SEEK_SET);
    	    if (bfs_data_size != fread(&bfs_tmp_data[i][0], sizeof(cl_long), bfs_data_size, fin[i])) {
    	        printf("fread failed");
    	    }
    	    fclose(fin[i]);
    }
    std::cout << "*6"<<endl;
    for (size_t i = 0; i < dataSize; i++) {
    	if(i > ali_num * level_addr /2){
            for(cl_uint pipe_i = 0; pipe_i < pipe_num; pipe_i++){
                bfs_data[pipe_i][i] = 0;
            }
    	}
    	else{
            for(cl_uint pipe_i = 0; pipe_i < pipe_num; pipe_i++){
                bfs_data[pipe_i][i] = bfs_tmp_data[pipe_i][i];
            }
    	}
    }
    printf("bfs_data sucess");
    std::cout << "bfs_data read successful!\n";


    // For Allocating Buffer to specific Global Memory Bank, user has to use cl_mem_ext_ptr_t
    // and provide the Banks
    std::vector<cl_mem_ext_ptr_t> inBufExt(pipe_num);
    std::vector<cl::Buffer> buffer_input(pipe_num);
    for(cl_uint pipe_i = 0; pipe_i < pipe_num; pipe_i++){
        inBufExt[pipe_i].obj = bfs_data[pipe_i].data();
        inBufExt[pipe_i].param = 0;
        inBufExt[pipe_i].flags = bank[pipe_i];
        OCL_CHECK(err, buffer_input[pipe_i]=cl::Buffer(
        	context,
                                     CL_MEM_READ_WRITE |
                                        CL_MEM_EXT_PTR_XILINX |
                                        CL_MEM_USE_HOST_PTR,
                                        sizeof(uint64_t) * dataSize,
                                        &inBufExt[pipe_i],
                                       &err));
        std::cout <<err<<"Buffers"<<pipe_i<<" successful!\n";
    }

    // These commands will allocate memory on the FPGA. The cl::Buffer objects can
    // be used to reference the memory locations on the device.
    //Creating Buffers
    for(cl_uint i = 0;i<pipe_num;i++){
    // Copy input data to Device Global Memory
    OCL_CHECK(err, err = q.enqueueMigrateMemObjects({buffer_input[i]},0/* 0 means from host*/));

    }
    q.finish();
    std::cout << err<<"enqueueMigrateMemObjects successful!\n";


    //Setting the kernel Arguments

    OCL_CHECK(err, err = (kernel_bfs).setArg(0, csr_c_addr));
    OCL_CHECK(err, err = (kernel_bfs).setArg(1, csr_r_addr));
    OCL_CHECK(err, err = (kernel_bfs).setArg(2, csc_c_addr));
    OCL_CHECK(err, err = (kernel_bfs).setArg(3, csc_r_addr));
    OCL_CHECK(err, err = (kernel_bfs).setArg(4, level_addr));
    OCL_CHECK(err, err = (kernel_bfs).setArg(5, node_num));
    OCL_CHECK(err, err = (kernel_bfs).setArg(6, push_to_pull_level));
    OCL_CHECK(err, err = (kernel_bfs).setArg(7, pull_to_push_level));
    OCL_CHECK(err, err = (kernel_bfs).setArg(8, root));
    for(cl_uint pipe_i = 0; pipe_i < pipe_num; pipe_i++){
    	OCL_CHECK(err, err = (kernel_bfs).setArg(9+pipe_i, buffer_input[pipe_i]));
    }


    // time begin
 	double kernel_time_in_sec = 0;
 	std::chrono::duration<double> kernel_time(0);
 	auto kernel_start = std::chrono::high_resolution_clock::now();


    //Start kernel
    OCL_CHECK(err, err = q.enqueueTask(kernel_bfs));
    std::cout << err<< "enqueueTask successful!\n";
    q.finish();


    // time end
    auto kernel_end = std::chrono::high_resolution_clock::now();
    kernel_time = std::chrono::duration<double>(kernel_end - kernel_start);
    kernel_time_in_sec = kernel_time.count();






    // Copy output data to Host Memory
    for(cl_uint i =0;i<pipe_num;i++){
    OCL_CHECK(err,
              err = q.enqueueMigrateMemObjects({buffer_input[i]},
                                               CL_MIGRATE_MEM_OBJECT_HOST));
    }
    q.finish();
    std::cout <<err<< "enqueueMigrateMemObjects successful!\n";
    //Read the level data
    cl_uint node_level = 0;
    cl_uint level_addr_act = level_addr * ali_num /2;
    cl_uint i_act = 0;


   std::cout << "read data to file successful!\n";
   std::cout << "kernel_count: "<<(bfs_data[0][0] & 0xffffffff) <<endl;
   std::cout << "kernel time in second is: " << kernel_time_in_sec << std::endl;

   double kernel_count_in_sec = bfs_data[0][0] &0xffffffff; // Number of cycles for BFS algorithm execution
   int level = ((bfs_data[0][0] >> 32)&0xffffffff);
   //kyle

   result /= (1000 / fre);            // to KB
   result /= kernel_count_in_sec; // to GBps
   std::cout << "THROUGHPUT = " << result << " GTep/s" << std::endl;
   std::cout << "level = " << level << std::endl;


   return EXIT_SUCCESS;

}
