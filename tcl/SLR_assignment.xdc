# remove_cells_from_pblock pblock_dynamic_region [get_cells [list pfm_top_i/dynamic_region/FinalBFS_32_1/inst/top_i/clk_wiz]]
remove_cells_from_pblock pblock_dynamic_region [get_cells [list pfm_top_i/dynamic_region/FinalBFS_32_1/inst/clk_wiz_1]]

# add_cells_to_pblock pblock_dynamic_SLR1 [get_cells [list pfm_top_i/dynamic_region/FinalBFS_32_1/inst/top_i/clk_wiz]] -clear_locs
add_cells_to_pblock pblock_dynamic_SLR1 [get_cells [list pfm_top_i/dynamic_region/FinalBFS_32_1/inst/clk_wiz_1]] -clear_locs
set_property CLOCK_DEDICATED_ROUTE ANY_CMT_COLUMN [get_nets pfm_top_i/static_region/base_clocking/clkwiz_kernel/inst/CLK_CORE_DRP_I/clk_inst/clk_out1]

add_cells_to_pblock pblock_dynamic_SLR0 [get_cells [list pfm_top_i/dynamic_region/FinalBFS_32_1/inst/top_i/SLR0_Mem]] -clear_locs
add_cells_to_pblock pblock_dynamic_SLR0 [get_cells [list pfm_top_i/dynamic_region/FinalBFS_32_1/inst/top_i/SLR0]] -clear_locs
add_cells_to_pblock pblock_dynamic_SLR1 [get_cells [list pfm_top_i/dynamic_region/FinalBFS_32_1/inst/top_i/SLR1]] -clear_locs
add_cells_to_pblock pblock_dynamic_SLR2 [get_cells [list pfm_top_i/dynamic_region/FinalBFS_32_1/inst/top_i/SLR2]] -clear_locs
# add_cells_to_pblock pblock_dynamic_SLR0 [get_cells [list pfm_top_i/dynamic_region/FinalBFS_32_1/inst/top_i/clk_wiz_SLR0]] -clear_locs
# add_cells_to_pblock pblock_dynamic_SLR1 [get_cells [list pfm_top_i/dynamic_region/FinalBFS_32_1/inst/top_i/clk_wiz_SLR1]] -clear_locs
# add_cells_to_pblock pblock_dynamic_SLR2 [get_cells [list pfm_top_i/dynamic_region/FinalBFS_32_1/inst/top_i/clk_wiz_SLR2]] -clear_locs


reset_property SEVERITY [get_drc_checks HDPR-5]

