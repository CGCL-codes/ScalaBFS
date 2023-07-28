SBT = sbt
CUR_DIR = $(shell pwd)
OBJ_DIR = $(CUR_DIR)/obj
PROJECTDIR = $(CUR_DIR)/project
BIN_DIR = $(CUR_DIR)/bin

PRO_DIR = project/$(pc)-$(pe)
HOST_DIR = $(CUR_DIR)/host

hdl: top # movev update_kernel movecpp

top: 
	$(SBT) -mem 51200 "runMain HBMGraph.Top" 
	python add_init.py

update_bitstream:
	cp $(HOST_DIR)/xcl2.* $(PRO_DIR)/FinalBFS_32/src/vitis_rtl_kernel/FinalBFS_32/
	cp $(HOST_DIR)/$(pc).cpp $(PRO_DIR)/FinalBFS_32/src/vitis_rtl_kernel/FinalBFS_32/host_example.cpp
	cd $(PRO_DIR)/FinalBFS_32/Hardware \
	&& g++ -std=c++0x -DVITIS_PLATFORM=xilinx_u280_xdma_201920_1 -D__USE_XOPEN2K8 -I/opt/xilinx/xrt/include/ -I/tools/Xilinx/Vivado/2019.2/include/ -O2 -Wall -c -fmessage-length=0 -o "src/vitis_rtl_kernel/FinalBFS_32/host_example.o" "../src/vitis_rtl_kernel/FinalBFS_32/host_example.cpp" \
	&& g++ -std=c++0x -DVITIS_PLATFORM=xilinx_u280_xdma_201920_1 -D__USE_XOPEN2K8 -I/opt/xilinx/xrt/include/ -I/tools/Xilinx/Vivado/2019.2/include/ -O2 -Wall -c -fmessage-length=0 -o "src/vitis_rtl_kernel/FinalBFS_32/xcl2.o" "../src/vitis_rtl_kernel/FinalBFS_32/xcl2.cpp" \
	&& g++ -o "FinalBFS_32" src/vitis_rtl_kernel/FinalBFS_32/host_example.o src/vitis_rtl_kernel/FinalBFS_32/xcl2.o -lxilinxopencl -lpthread -lrt -lstdc++ -lmpfr -lgmp -lhlsmc++-GCC46 -lIp_floating_point_v7_0_bitacc_cmodel -lIp_xfft_v9_1_bitacc_cmodel -lIp_fir_compiler_v7_2_bitacc_cmodel -lIp_dds_compiler_v6_0_bitacc_cmodel -L/opt/xilinx/xrt/lib/ -L/tools/Xilinx/Vivado/2019.2/lnx64/tools/fpo_v7_0 -L/tools/Xilinx/Vivado/2019.2/lnx64/lib/csim -L/tools/Xilinx/Vivado/2019.2/lnx64/tools/dds_v6_0 -L/tools/Xilinx/Vivado/2019.2/lnx64/tools/fir_v7_0 -L/tools/Xilinx/Vivado/2019.2/lnx64/tools/fft_v9_1 -Wl,-rpath-link,/opt/xilinx/xrt/lib -Wl,-rpath,/tools/Xilinx/Vivado/2019.2/lnx64/lib/csim -Wl,-rpath,/tools/Xilinx/Vivado/2019.2/lnx64/tools/fpo_v7_0 -Wl,-rpath,/tools/Xilinx/Vivado/2019.2/lnx64/tools/fft_v9_1 -Wl,-rpath,/tools/Xilinx/Vivado/2019.2/lnx64/tools/fir_v7_0 -Wl,-rpath,/tools/Xilinx/Vivado/2019.2/lnx64/tools/dds_v6_0\

update_obj:
	g++ -std=c++0x -DVITIS_PLATFORM=xilinx_u280_xdma_201920_1 -D__USE_XOPEN2K8 -I/opt/xilinx/xrt/include/ -I/tools/Xilinx/Vivado/2019.2/include/ -O2 -Wall -c -fmessage-length=0 -o "$(OBJ_DIR)/pc$(pc).o" "$(HOST_DIR)/pc$(pc).cpp" 
	g++ -std=c++0x -DVITIS_PLATFORM=xilinx_u280_xdma_201920_1 -D__USE_XOPEN2K8 -I/opt/xilinx/xrt/include/ -I/tools/Xilinx/Vivado/2019.2/include/ -O2 -Wall -c -fmessage-length=0 -o "$(OBJ_DIR)/xcl2.o" "$(HOST_DIR)/xcl2.cpp" 
	g++ -o "$(OBJ_DIR)/pc$(pc)" $(OBJ_DIR)/pc$(pc).o $(OBJ_DIR)/xcl2.o -lxilinxopencl -lpthread -lrt -lstdc++ -lmpfr -lgmp -lhlsmc++-GCC46 -lIp_floating_point_v7_0_bitacc_cmodel -lIp_xfft_v9_1_bitacc_cmodel -lIp_fir_compiler_v7_2_bitacc_cmodel -lIp_dds_compiler_v6_0_bitacc_cmodel -L/opt/xilinx/xrt/lib/ -L/tools/Xilinx/Vivado/2019.2/lnx64/tools/fpo_v7_0 -L/tools/Xilinx/Vivado/2019.2/lnx64/lib/csim -L/tools/Xilinx/Vivado/2019.2/lnx64/tools/dds_v6_0 -L/tools/Xilinx/Vivado/2019.2/lnx64/tools/fir_v7_0 -L/tools/Xilinx/Vivado/2019.2/lnx64/tools/fft_v9_1 -Wl,-rpath-link,/opt/xilinx/xrt/lib -Wl,-rpath,/tools/Xilinx/Vivado/2019.2/lnx64/lib/csim -Wl,-rpath,/tools/Xilinx/Vivado/2019.2/lnx64/tools/fpo_v7_0 -Wl,-rpath,/tools/Xilinx/Vivado/2019.2/lnx64/tools/fft_v9_1 -Wl,-rpath,/tools/Xilinx/Vivado/2019.2/lnx64/tools/fir_v7_0 -Wl,-rpath,/tools/Xilinx/Vivado/2019.2/lnx64/tools/dds_v6_0

run:
	$(OBJ_DIR)/pc$(pc) $(BIN_DIR)/$(pc)-$(pe).xclbin $(graph) $(root) $(push2pull) $(pull2push) \
	|  tee -a throughput_total.txt | grep '^THROUGHPUT' | tee -a throughput_$(pc)_$(pe).txt # && sed -n 240p xclbin.run_summary | tee -a throughput_ch1-2.txt

movev:
	cp Top.v $(PRO_DIR)/FinalBFS_32/vivado_rtl_kernel/FinalBFS_32_ex/FinalBFS_32_ex.srcs/sources_1/imports/bfs_u280/
	cp Top.v $(PRO_DIR)/FinalBFS_32/vivado_rtl_kernel/FinalBFS_32_ex/FinalBFS_32_ex.srcs/sources_1/imports/imports/bfs_u280/


update_kernel:
	rm -f $(PRO_DIR)/FinalBFS_32/vivado_rtl_kernel/FinalBFS_32_ex/exports/FinalBFS_32.xo
	echo "open_project $(PRO_DIR)/FinalBFS_32/vivado_rtl_kernel/FinalBFS_32_ex/FinalBFS_32_ex.xpr" > update_kernel.tcl
	echo "update_compile_order -fileset sources_1" >> update_kernel.tcl
	# echo "set_property -dict [list CONFIG.CLKOUT1_REQUESTED_OUT_FREQ {250.000} CONFIG.MMCM_DIVCLK_DIVIDE {9} CONFIG.MMCM_CLKFBOUT_MULT_F {23.750} CONFIG.MMCM_CLKOUT0_DIVIDE_F {4.750} CONFIG.CLKOUT1_JITTER {114.098} CONFIG.CLKOUT1_PHASE_ERROR {153.018}] [get_ips clk_wiz_1]" >> update_kernel.tcl
	echo "source -notrace $(PRO_DIR)/FinalBFS_32/vivado_rtl_kernel/FinalBFS_32_ex/imports/package_kernel.tcl" >> update_kernel.tcl
	echo "package_project $(PRO_DIR)/FinalBFS_32/vivado_rtl_kernel/FinalBFS_32_ex/FinalBFS_32 mycompany.com kernel FinalBFS_32" >> update_kernel.tcl
	echo "package_xo  -xo_path $(PRO_DIR)/FinalBFS_32/vivado_rtl_kernel/FinalBFS_32_ex/exports/FinalBFS_32.xo -kernel_name FinalBFS_32 -ip_directory $(PRO_DIR)/FinalBFS_32/vivado_rtl_kernel/FinalBFS_32_ex/FinalBFS_32 -kernel_xml $(PRO_DIR)/FinalBFS_32/vivado_rtl_kernel/FinalBFS_32_ex/imports/kernel.xml" >> update_kernel.tcl
	echo "file mkdir $(PRO_DIR)/FinalBFS_32/src/vitis_rtl_kernel/FinalBFS_32" >> update_kernel.tcl
	echo "exit" >> update_kernel.tcl
	vivado -mode tcl -source update_kernel.tcl




clean:
	rm -f *.v
	rm -f *.json
	rm -f *.fir
	rm -f .*.swo
	rm -f .*.swp
	rm -rf $(OBJ_DIR)
	# rm -rf $(PROJECTDIR)
	rm -rf $(TARGETDIR)
	rm -f *.log

clear:
	rm -f *.json
	rm -f *.fir
	rm -f .*.swo
	rm -f .*.swp
	rm -f $(OBJ_DIR)/*.json
	rm -f $(OBJ_DIR)/*.fir
	rm -f $(OBJ_DIR)/.*.swo
	rm -f $(OBJ_DIR)/.*.swp
	




