# clk_wiz_1 450-225

create_ip -name clk_wiz -vendor xilinx.com -library ip -version 6.0 -module_name clk_wiz_1
set_property -dict [list \
  CONFIG.CLKIN1_JITTER_PS {22.22} \
  CONFIG.CLKOUT1_DRIVES {BUFG} \
  CONFIG.CLKOUT1_JITTER {88.560} \
  CONFIG.CLKOUT1_PHASE_ERROR {80.491} \
  CONFIG.CLKOUT1_REQUESTED_OUT_FREQ {225.000} \
  CONFIG.CLK_OUT1_PORT {clk_sys} \
  CONFIG.Component_Name {clk_wiz_1} \
  CONFIG.FEEDBACK_SOURCE {FDBK_AUTO} \
  CONFIG.MMCM_CLKFBOUT_MULT_F {5.375} \
  CONFIG.MMCM_CLKIN1_PERIOD {2.222} \
  CONFIG.MMCM_CLKIN2_PERIOD {10.0} \
  CONFIG.MMCM_CLKOUT0_DIVIDE_F {5.375} \
  CONFIG.MMCM_DIVCLK_DIVIDE {2} \
  CONFIG.OPTIMIZE_CLOCKING_STRUCTURE_EN {true} \
  CONFIG.PRIMARY_PORT {clock} \
  CONFIG.PRIM_IN_FREQ {450.000} \
  CONFIG.USE_LOCKED {false} \
  CONFIG.USE_RESET {false} \
] [get_ips clk_wiz_1]
generate_target {instantiation_template} [get_files [get_property directory [current_project]]/scalabfs_ex.srcs/sources_1/ip/clk_wiz_1_2/clk_wiz_1.xci]



# fifo generator

create_ip -name fifo_generator -vendor xilinx.com -library ip -version 13.2 -module_name fifo_generator_writeAddr
set_property -dict [list CONFIG.Input_Data_Width {78} CONFIG.Input_Depth {16} CONFIG.Output_Data_Width {78} CONFIG.Output_Depth {16} CONFIG.Data_Count_Width {4} CONFIG.Write_Data_Count_Width {4} CONFIG.Read_Data_Count_Width {4} CONFIG.Full_Threshold_Assert_Value {15} CONFIG.Full_Threshold_Negate_Value {14} CONFIG.Performance_Options {First_Word_Fall_Through} CONFIG.Fifo_Implementation {Independent_Clocks_Distributed_RAM}  ] [get_ips fifo_generator_writeAddr]
generate_target {instantiation_template} [get_files [get_property directory [current_project]]/FinalBFS_32_ex.srcs/sources_1/ip/fifo_generator_writeAddr/fifo_generator_writeAddr.xci]
update_compile_order -fileset sources_1


create_ip -name fifo_generator -vendor xilinx.com -library ip -version 13.2 -module_name fifo_generator_writeData
set_property -dict [list CONFIG.Input_Data_Width {289} CONFIG.Input_Depth {16} CONFIG.Output_Data_Width {289} CONFIG.Output_Depth {16} CONFIG.Data_Count_Width {4} CONFIG.Write_Data_Count_Width {4} CONFIG.Read_Data_Count_Width {4} CONFIG.Full_Threshold_Assert_Value {15} CONFIG.Full_Threshold_Negate_Value {14} CONFIG.Performance_Options {First_Word_Fall_Through} CONFIG.Fifo_Implementation {Independent_Clocks_Distributed_RAM}  ] [get_ips fifo_generator_writeData]
generate_target {instantiation_template} [get_files [get_property directory [current_project]]/FinalBFS_32_ex.srcs/sources_1/ip/fifo_generator_writeData/fifo_generator_writeData.xci]
update_compile_order -fileset sources_1

create_ip -name fifo_generator -vendor xilinx.com -library ip -version 13.2 -module_name fifo_generator_writeResp
set_property -dict [list CONFIG.Input_Data_Width {8} CONFIG.Input_Depth {16} CONFIG.Output_Data_Width {8} CONFIG.Output_Depth {16} CONFIG.Data_Count_Width {4} CONFIG.Write_Data_Count_Width {4} CONFIG.Read_Data_Count_Width {4} CONFIG.Full_Threshold_Assert_Value {15} CONFIG.Full_Threshold_Negate_Value {14} CONFIG.Performance_Options {First_Word_Fall_Through} CONFIG.Fifo_Implementation {Independent_Clocks_Distributed_RAM}  ] [get_ips fifo_generator_writeResp]
generate_target {instantiation_template} [get_files [get_property directory [current_project]]/FinalBFS_32_ex.srcs/sources_1/ip/fifo_generator_writeResp/fifo_generator_writeResp.xci]
update_compile_order -fileset sources_1

create_ip -name fifo_generator -vendor xilinx.com -library ip -version 13.2 -module_name fifo_generator_readAddr
set_property -dict [list CONFIG.Input_Data_Width {78} CONFIG.Input_Depth {16} CONFIG.Output_Data_Width {78} CONFIG.Output_Depth {16} CONFIG.Data_Count_Width {4} CONFIG.Write_Data_Count_Width {4} CONFIG.Read_Data_Count_Width {4} CONFIG.Full_Threshold_Assert_Value {15} CONFIG.Full_Threshold_Negate_Value {14} CONFIG.Performance_Options {First_Word_Fall_Through} CONFIG.Fifo_Implementation {Independent_Clocks_Distributed_RAM}  ] [get_ips fifo_generator_readAddr]
generate_target {instantiation_template} [get_files [get_property directory [current_project]]/FinalBFS_32_ex.srcs/sources_1/ip/fifo_generator_readAddr/fifo_generator_readAddr.xci]
update_compile_order -fileset sources_1

create_ip -name fifo_generator -vendor xilinx.com -library ip -version 13.2 -module_name fifo_generator_readData
set_property -dict [list CONFIG.Input_Data_Width {64} CONFIG.Input_Depth {16} CONFIG.Output_Data_Width {64} CONFIG.Output_Depth {16} CONFIG.Data_Count_Width {4} CONFIG.Write_Data_Count_Width {4} CONFIG.Read_Data_Count_Width {4} CONFIG.Full_Threshold_Assert_Value {15} CONFIG.Full_Threshold_Negate_Value {14} CONFIG.Performance_Options {First_Word_Fall_Through} CONFIG.Fifo_Implementation {Independent_Clocks_Distributed_RAM}  ] [get_ips fifo_generator_readData]
generate_target {instantiation_template} [get_files [get_property directory [current_project]]/FinalBFS_32_ex.srcs/sources_1/ip/fifo_generator_readData/fifo_generator_readData.xci]
update_compile_order -fileset sources_1

create_ip -name fifo_generator -vendor xilinx.com -library ip -version 13.2 -module_name fifo_generator_src
set_property -dict [list CONFIG.Input_Data_Width {32} CONFIG.Input_Depth {16} CONFIG.Output_Data_Width {32} CONFIG.Output_Depth {16} CONFIG.Data_Count_Width {4} CONFIG.Write_Data_Count_Width {4} CONFIG.Read_Data_Count_Width {4} CONFIG.Full_Threshold_Assert_Value {15} CONFIG.Full_Threshold_Negate_Value {14} CONFIG.Performance_Options {First_Word_Fall_Through} CONFIG.Fifo_Implementation {Independent_Clocks_Distributed_RAM}  ] [get_ips fifo_generator_src]
generate_target {instantiation_template} [get_files [get_property directory [current_project]]/FinalBFS_32_ex.srcs/sources_1/ip/fifo_generator_src/fifo_generator_src.xci]
update_compile_order -fileset sources_1


create_ip -name fifo_generator -vendor xilinx.com -library ip -version 13.2 -module_name fifo_generator_readData_syn
set_property -dict [list CONFIG.Component_Name {fifo_generator_readData_syn} CONFIG.Fifo_Implementation {Common_Clock_Distributed_RAM} CONFIG.Performance_Options {First_Word_Fall_Through} CONFIG.Input_Data_Width {64} CONFIG.Input_Depth {16} CONFIG.Output_Data_Width {64} CONFIG.Output_Depth {16} CONFIG.Use_Embedded_Registers {false} CONFIG.Reset_Type {Asynchronous_Reset} CONFIG.Full_Flags_Reset_Value {1} CONFIG.Use_Extra_Logic {true} CONFIG.Data_Count_Width {5} CONFIG.Write_Data_Count_Width {5} CONFIG.Read_Data_Count_Width {5} CONFIG.Full_Threshold_Assert_Value {15} CONFIG.Full_Threshold_Negate_Value {14} CONFIG.Empty_Threshold_Assert_Value {4} CONFIG.Empty_Threshold_Negate_Value {5}] [get_ips fifo_generator_readData_syn]
generate_target {instantiation_template} [get_files [get_property directory [current_project]]/FinalBFS_32/vivado_rtl_kernel/FinalBFS_32_ex/FinalBFS_32_ex.srcs/sources_1/ip/fifo_generator_readData_syn/fifo_generator_readData_syn.xci]


create_ip -name fifo_generator -vendor xilinx.com -library ip -version 13.2 -module_name fifo_cxb_24
set_property -dict [list CONFIG.Component_Name {fifo_cxb_24} CONFIG.Fifo_Implementation {Common_Clock_Distributed_RAM} CONFIG.Performance_Options {First_Word_Fall_Through} CONFIG.Input_Data_Width {24} CONFIG.Input_Depth {16} CONFIG.Output_Data_Width {24} CONFIG.Output_Depth {16} CONFIG.Use_Embedded_Registers {false} CONFIG.Reset_Type {Asynchronous_Reset} CONFIG.Full_Flags_Reset_Value {1} CONFIG.Use_Extra_Logic {true} CONFIG.Data_Count_Width {5} CONFIG.Write_Data_Count_Width {5} CONFIG.Read_Data_Count_Width {5} CONFIG.Full_Threshold_Assert_Value {15} CONFIG.Full_Threshold_Negate_Value {14} CONFIG.Empty_Threshold_Assert_Value {4} CONFIG.Empty_Threshold_Negate_Value {5}] [get_ips fifo_cxb_24]
generate_target {instantiation_template} [get_files [get_property directory [current_project]]/FinalBFS_32_ex.srcs/sources_1/ip/fifo_cxb_24/fifo_cxb_24.xci]


create_ip -name fifo_generator -vendor xilinx.com -library ip -version 13.2 -module_name fifo_cxb_48
set_property -dict [list CONFIG.Component_Name {fifo_cxb_48} CONFIG.Fifo_Implementation {Common_Clock_Distributed_RAM} CONFIG.Performance_Options {First_Word_Fall_Through} CONFIG.Input_Data_Width {48} CONFIG.Input_Depth {16} CONFIG.Output_Data_Width {48} CONFIG.Output_Depth {16} CONFIG.Use_Embedded_Registers {false} CONFIG.Reset_Type {Asynchronous_Reset} CONFIG.Full_Flags_Reset_Value {1} CONFIG.Use_Extra_Logic {true} CONFIG.Data_Count_Width {5} CONFIG.Write_Data_Count_Width {5} CONFIG.Read_Data_Count_Width {5} CONFIG.Full_Threshold_Assert_Value {15} CONFIG.Full_Threshold_Negate_Value {14} CONFIG.Empty_Threshold_Assert_Value {4} CONFIG.Empty_Threshold_Negate_Value {5}] [get_ips fifo_cxb_48]
generate_target {instantiation_template} [get_files [get_property directory [current_project]]/FinalBFS_32_ex.srcs/sources_1/ip/fifo_cxb_48/fifo_cxb_48.xci]
