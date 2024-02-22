// FIFOs used for inter-SLR connections
module R_array_index_01_slr0 (
    input clock,
    input reset,
    input enq_valid,
    output enq_ready,
    input [31:0] enq_bits,
    output deq_valid,
    input deq_ready,
    output reg [31:0] deq_bits
);
    reg [31:0] mem [0:7];
    reg enq_ready_inside;

    reg [2:0] read_ptr, write_ptr, counter;
    always @(posedge clock or posedge reset) begin
        if(reset) begin
            read_ptr    <= 0;
            write_ptr   <= 0;
            counter     <= 0;
            // deq_bits    <= 0;
            enq_ready_inside   <= 1;
            // deq_valid   <= 0;
            mem[0]      <= 0;
            mem[1]      <= 0;
            mem[2]      <= 0;
            mem[3]      <= 0;
            mem[4]      <= 0;
            mem[5]      <= 0;
            mem[6]      <= 0;
            mem[7]      <= 0;
        end
        else begin
            case({deq_ready, enq_valid})
                2'b00:  counter <= counter;
                2'b01:  begin
                    if(enq_ready_inside) begin
                        mem[write_ptr] <= enq_bits;
                        write_ptr <= write_ptr + 1;
                        counter <= counter + 1;
                    end
                end
                2'b10:  begin
                    if(deq_valid) begin
                        deq_bits <= mem[read_ptr];
                        read_ptr <= read_ptr + 1;
                        counter <= counter - 1;
                    end
                end
                2'b11:  begin
                    if(~deq_valid) begin
                        mem[write_ptr] <= enq_bits;
                        write_ptr <= write_ptr + 1;
                        counter <= counter + 1;
                    end
                    else if(~enq_ready_inside) begin
                        deq_bits <= mem[read_ptr];
                        read_ptr <= read_ptr + 1;
                        counter <= counter - 1;
                    end
                    else begin
                        mem[write_ptr] <= enq_bits;
                        write_ptr <= write_ptr + 1;
                        deq_bits <= mem[read_ptr];
                        read_ptr <= read_ptr + 1;
                        counter <= counter;
                    end
                end
            endcase
            // deq_valid <= ~empty;
            enq_ready_inside <= (counter < 6) ? 1 : 0;
        end
    end
    // assign enq_ready_inside = (counter != 7) ? 1 : 0;
    assign enq_ready = (counter < 6) ? 1 : 0;
    assign deq_valid = (counter != 0) ? 1 : 0;
    // assign deq_bits = mem[read_ptr];
endmodule

module R_array_index_01_slr1 (
    input clock,
    input reset,
    input enq_valid,
    output enq_ready,
    input [31:0] enq_bits,
    output deq_valid,
    input deq_ready,
    output reg [31:0] deq_bits
);
    reg [31:0] mem [0:7];
    // reg enq_ready_inside;

    reg [2:0] read_ptr, write_ptr, counter;
    always @(posedge clock or posedge reset) begin
        if(reset) begin
            read_ptr    <= 0;
            write_ptr   <= 0;
            counter     <= 0;
            // deq_bits    <= 0;
            // enq_ready_inside   <= 1;
            // deq_valid   <= 0;
            mem[0]      <= 0;
            mem[1]      <= 0;
            mem[2]      <= 0;
            mem[3]      <= 0;
            mem[4]      <= 0;
            mem[5]      <= 0;
            mem[6]      <= 0;
            mem[7]      <= 0;
        end
        else begin
            case({deq_ready, enq_valid})
                2'b00:  counter <= counter;
                2'b01:  begin
                    if(enq_ready) begin
                        mem[write_ptr] <= enq_bits;
                        write_ptr <= write_ptr + 1;
                        counter <= counter + 1;
                    end
                end
                2'b10:  begin
                    if(deq_valid) begin
                        deq_bits <= mem[read_ptr];
                        read_ptr <= read_ptr + 1;
                        counter <= counter - 1;
                    end
                end
                2'b11:  begin
                    if(~deq_valid) begin
                        mem[write_ptr] <= enq_bits;
                        write_ptr <= write_ptr + 1;
                        counter <= counter + 1;
                    end
                    else if(~enq_ready) begin
                        deq_bits <= mem[read_ptr];
                        read_ptr <= read_ptr + 1;
                        counter <= counter - 1;
                    end
                    else begin
                        mem[write_ptr] <= enq_bits;
                        write_ptr <= write_ptr + 1;
                        deq_bits <= mem[read_ptr];
                        read_ptr <= read_ptr + 1;
                        counter <= counter;
                    end
                end
            endcase
            // deq_valid <= ~empty;
            // enq_ready_inside <= (counter < 6) ? 1 : 0;
        end
    end
    // assign enq_ready_inside = (counter != 7) ? 1 : 0;
    assign enq_ready = (counter != 7) ? 1 : 0;
    assign deq_valid = (counter != 0) ? 1 : 0;
    // assign deq_bits = mem[read_ptr];
endmodule

module R_array_index_02_slr0 (
    input clock,
    input reset,
    input enq_valid,
    output enq_ready,
    input [31:0] enq_bits,
    output deq_valid,
    input deq_ready,
    output reg [31:0] deq_bits
);
    reg [31:0] mem [0:7];
    reg enq_ready_inside;

    reg [2:0] read_ptr, write_ptr, counter;
    always @(posedge clock or posedge reset) begin
        if(reset) begin
            read_ptr    <= 0;
            write_ptr   <= 0;
            counter     <= 0;
            // deq_bits    <= 0;
            enq_ready_inside   <= 1;
            // deq_valid   <= 0;
            mem[0]      <= 0;
            mem[1]      <= 0;
            mem[2]      <= 0;
            mem[3]      <= 0;
            mem[4]      <= 0;
            mem[5]      <= 0;
            mem[6]      <= 0;
            mem[7]      <= 0;
        end
        else begin
            case({deq_ready, enq_valid})
                2'b00:  counter <= counter;
                2'b01:  begin
                    if(enq_ready_inside) begin
                        mem[write_ptr] <= enq_bits;
                        write_ptr <= write_ptr + 1;
                        counter <= counter + 1;
                    end
                end
                2'b10:  begin
                    if(deq_valid) begin
                        deq_bits <= mem[read_ptr];
                        read_ptr <= read_ptr + 1;
                        counter <= counter - 1;
                    end
                end
                2'b11:  begin
                    if(~deq_valid) begin
                        mem[write_ptr] <= enq_bits;
                        write_ptr <= write_ptr + 1;
                        counter <= counter + 1;
                    end
                    else if(~enq_ready_inside) begin
                        deq_bits <= mem[read_ptr];
                        read_ptr <= read_ptr + 1;
                        counter <= counter - 1;
                    end
                    else begin
                        mem[write_ptr] <= enq_bits;
                        write_ptr <= write_ptr + 1;
                        deq_bits <= mem[read_ptr];
                        read_ptr <= read_ptr + 1;
                        counter <= counter;
                    end
                end
            endcase
            // deq_valid <= ~empty;
            enq_ready_inside <= (counter < 6) ? 1 : 0;
        end
    end
    // assign enq_ready_inside = (counter != 7) ? 1 : 0;
    assign enq_ready = (counter < 6) ? 1 : 0;
    assign deq_valid = (counter != 0) ? 1 : 0;
    // assign deq_bits = mem[read_ptr];
endmodule

module R_array_index_02_slr1 (
    input clock,
    input reset,
    input enq_valid,
    output enq_ready,
    input [31:0] enq_bits,
    output deq_valid,
    input deq_ready,
    output reg [31:0] deq_bits
);
    reg [31:0] mem [0:7];
    reg enq_ready_inside;

    reg [2:0] read_ptr, write_ptr, counter;
    always @(posedge clock or posedge reset) begin
        if(reset) begin
            read_ptr    <= 0;
            write_ptr   <= 0;
            counter     <= 0;
            // deq_bits    <= 0;
            enq_ready_inside   <= 1;
            // deq_valid   <= 0;
            mem[0]      <= 0;
            mem[1]      <= 0;
            mem[2]      <= 0;
            mem[3]      <= 0;
            mem[4]      <= 0;
            mem[5]      <= 0;
            mem[6]      <= 0;
            mem[7]      <= 0;
        end
        else begin
            case({deq_ready, enq_valid})
                2'b00:  counter <= counter;
                2'b01:  begin
                    if(enq_ready_inside) begin
                        mem[write_ptr] <= enq_bits;
                        write_ptr <= write_ptr + 1;
                        counter <= counter + 1;
                    end
                end
                2'b10:  begin
                    if(deq_valid) begin
                        deq_bits <= mem[read_ptr];
                        read_ptr <= read_ptr + 1;
                        counter <= counter - 1;
                    end
                end
                2'b11:  begin
                    if(~deq_valid) begin
                        mem[write_ptr] <= enq_bits;
                        write_ptr <= write_ptr + 1;
                        counter <= counter + 1;
                    end
                    else if(~enq_ready_inside) begin
                        deq_bits <= mem[read_ptr];
                        read_ptr <= read_ptr + 1;
                        counter <= counter - 1;
                    end
                    else begin
                        mem[write_ptr] <= enq_bits;
                        write_ptr <= write_ptr + 1;
                        deq_bits <= mem[read_ptr];
                        read_ptr <= read_ptr + 1;
                        counter <= counter;
                    end
                end
            endcase
            // deq_valid <= ~empty;
            enq_ready_inside <= (counter < 6) ? 1 : 0;
        end
    end
    // assign enq_ready_inside = (counter != 7) ? 1 : 0;
    assign enq_ready = (counter < 6) ? 1 : 0;
    assign deq_valid = (counter != 0) ? 1 : 0;
    // assign deq_bits = mem[read_ptr];
endmodule

module R_array_index_02_slr2 (
    input clock,
    input reset,
    input enq_valid,
    output enq_ready,
    input [31:0] enq_bits,
    output deq_valid,
    input deq_ready,
    output reg [31:0] deq_bits
);
    reg [31:0] mem [0:7];
    // reg enq_ready_inside;

    reg [2:0] read_ptr, write_ptr, counter;
    always @(posedge clock or posedge reset) begin
        if(reset) begin
            read_ptr    <= 0;
            write_ptr   <= 0;
            counter     <= 0;
            // deq_bits    <= 0;
            // enq_ready_inside   <= 1;
            // deq_valid   <= 0;
            mem[0]      <= 0;
            mem[1]      <= 0;
            mem[2]      <= 0;
            mem[3]      <= 0;
            mem[4]      <= 0;
            mem[5]      <= 0;
            mem[6]      <= 0;
            mem[7]      <= 0;
        end
        else begin
            case({deq_ready, enq_valid})
                2'b00:  counter <= counter;
                2'b01:  begin
                    if(enq_ready) begin
                        mem[write_ptr] <= enq_bits;
                        write_ptr <= write_ptr + 1;
                        counter <= counter + 1;
                    end
                end
                2'b10:  begin
                    if(deq_valid) begin
                        deq_bits <= mem[read_ptr];
                        read_ptr <= read_ptr + 1;
                        counter <= counter - 1;
                    end
                end
                2'b11:  begin
                    if(~deq_valid) begin
                        mem[write_ptr] <= enq_bits;
                        write_ptr <= write_ptr + 1;
                        counter <= counter + 1;
                    end
                    else if(~enq_ready) begin
                        deq_bits <= mem[read_ptr];
                        read_ptr <= read_ptr + 1;
                        counter <= counter - 1;
                    end
                    else begin
                        mem[write_ptr] <= enq_bits;
                        write_ptr <= write_ptr + 1;
                        deq_bits <= mem[read_ptr];
                        read_ptr <= read_ptr + 1;
                        counter <= counter;
                    end
                end
            endcase
            // deq_valid <= ~empty;
            // enq_ready_inside <= (counter < 6) ? 1 : 0;
        end
    end
    // assign enq_ready_inside = (counter != 7) ? 1 : 0;
    assign enq_ready = (counter != 7) ? 1 : 0;
    assign deq_valid = (counter != 0) ? 1 : 0;
    // assign deq_bits = mem[read_ptr];
endmodule