#include <cstdio>
#include <cstdlib>
#include <iostream>
#include <string>
#include <cstring>
#include <vector>
#include <utility>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/mman.h>
#include <sys/timeb.h>
#include <omp.h>

using namespace std;

typedef unsigned long long uint64;
typedef long long int64;
typedef unsigned int uint32;

FILE *flog = NULL;
FILE *fdebug = NULL;

struct timeb time1, time2;
uint64 sec;
int msec;

int ch_num;
uint32 pe_num;
uint32 ali_num;
int divide_num;

vector<vector<int>> graph_csc;
vector<vector<int>> graph_csr;
unsigned node_num = 0;
unsigned vertex_num = 0;
uint64 edge_num = 0;
bool start_with_0 = false;

uint32 *cscData;
uint32 *cscIndices;
uint64 *cscInfo;
int max_edge_csc = 0;

uint32 *csrData;
uint32 *csrIndices;
uint64 *csrInfo;
int max_edge_csr = 0;

unsigned int csr_r_addr = 0;
unsigned int csr_c_addr = 0;
unsigned int csc_r_addr = 0;
unsigned int csc_c_addr = 0;
unsigned int level_addr = 0;


static void measure(struct timeb &start, struct timeb &end, uint64 &sec, int &msec)
{
	sec = end.time - start.time;
	msec = end.millitm - start.millitm;
	if (msec < 0) {
		msec += 1000;
		sec -= 1;
	}
}


static inline bool isNumber(char ch) {
	return (ch >= '0' && ch <= '9');
}

static void readGraphFile(const char *filename, bool directed)
{
	ftime(&time1);

	int fd = open(filename, O_RDONLY);
	if (fd < 0) {
		fprintf(stderr, "Error: can't open graph file %s!\n", filename);
		exit(1);
	}
	struct stat st;
	fstat(fd, &st);
	readahead(fd, 0, st.st_size);
	char *mapaddr = (char *) mmap(NULL, st.st_size, PROT_READ, MAP_SHARED|MAP_POPULATE, fd, 0);
	if (mapaddr == MAP_FAILED) {
		fprintf(stderr, "Error: can't map file %s!\n", filename);
		exit(1);
	}

	#define TASK_NUM	40
	char *addr[TASK_NUM + 1];
	unsigned max[TASK_NUM];		// of node number
	int task_num = TASK_NUM;
	uint64 chunk_size = st.st_size / TASK_NUM;
	for (int i = 0; i < TASK_NUM; i++) {			// cut into chunks
		addr[i] = mapaddr + i * chunk_size;
	}
	addr[TASK_NUM] = mapaddr + st.st_size;
	for (int i = 1; i < task_num; i++) {			// align to '\n' for each task
		char *&p = addr[i];
		while (*p++ != '\n')
			;
		int c = 0;
		for (int j = i + 1; j <= task_num; j++) {	// check for overlap
			if (p >= addr[j])
				c++;
		}
		if (c != 0) {
			task_num -= c;
			for (int j = i + 1; j <= task_num; j++) {
				addr[j] = addr[j + c];
			}
		}
	}
	// for (char *&p = addr[0]; *p++ != '\n'; )		// skip the first line
	// 	;

	vector<vector<pair<unsigned, unsigned>>> edge(task_num);
	// bool start_with_0 = false;
	int number = 0;
	#pragma omp parallel for
	for (int i = 0; i < task_num; i++) {
		char *s = addr[i], *e = addr[i + 1];
		auto &vec = edge[i];
		unsigned m = 0;
		
		// while (s < e) {
		// 	while (*s++ != '\n')
		// 		;
		// 	m++;
		// }
		// vec.reserve(m);
		// s = addr[i], m = 0;
		for (;;) {
			while (s < e && !isNumber(*s))
				s++;
			if (s >= e)
				break;
			unsigned u = atoi(s);
			while (isNumber(*s))
				s++;
			unsigned v = atoi(s);
			while (*s++ != '\n')
				;
			if (u > m)
				m = u;
			if (v > m)
				m = v;
			if (u == 0 || v == 0)
				start_with_0 = true;
			vec.push_back(make_pair(u, v));
			// number ++;
			if (!directed)
				vec.push_back(make_pair(v, u));
				// number ++;
		}
		max[i] = m;
	}
	// printf("number %d\n",number);
	munmap(mapaddr, st.st_size);
	close(fd);

	ftime(&time2);
	measure(time1, time2, sec, msec);
	printf("    %-24s %llu s %d ms\n", "map and read", sec, msec);

	for (int i = 0; i < task_num; i++) {
		edge_num += edge[i].size();
		if (max[i] > node_num)
			node_num = max[i];
	}
	graph_csc.resize(node_num + 1);
	graph_csr.resize(node_num + 1);
	int (*degree)[2] = (int (*)[2]) malloc((node_num + 1) * sizeof(int) * 2);	// [0]:out, [1]:in
	memset(degree, 0, (node_num + 1) * sizeof(int) * 2);
	#pragma omp parallel for
	for (int i = 0; i < task_num; i++) {			// statistics for reserve()
		for (auto &e : edge[i]) {
			__sync_fetch_and_add(&degree[e.first][0], 1);
			__sync_fetch_and_add(&degree[e.second][1], 1);
		}
	}
	for (int i = 0; i <= node_num; i++) {			// to speed up push_back()
		graph_csc[i].reserve(degree[i][1]);
		graph_csr[i].reserve(degree[i][0]);
	}
	free(degree);

	ftime(&time1);
	measure(time2, time1, sec, msec);
	printf("    %-24s %llu s %d ms\n", "build vector", sec, msec);
	
	omp_lock_t *omplocks = (omp_lock_t *) malloc((node_num + 1) * sizeof(omp_lock_t));
	for (int i = 0; i <= node_num; i++) {
		omp_init_lock(&omplocks[i]);
	}
	#pragma omp parallel for
	for (int i = 0; i < task_num; i++) {
		for (auto &e : edge[i]) {
			unsigned u = e.first;
			unsigned v = e.second;
		omp_set_lock(&omplocks[v]);
			graph_csc[v].push_back(u);
		omp_unset_lock(&omplocks[v]);
		omp_set_lock(&omplocks[u]);
			graph_csr[u].push_back(v);
		omp_unset_lock(&omplocks[u]);
		}
	}
	for (int i = 0; i <= node_num; i++) {
		omp_destroy_lock(&omplocks[i]);
	}
	free(omplocks);

	ftime(&time2);
	measure(time1, time2, sec, msec);
	printf("    %-24s %llu s %d ms\n", "process edge list", sec, msec);

	if (start_with_0)
		vertex_num = node_num + 1;
	else
		vertex_num = node_num;
	printf("vertex number: %u\nedge number: %llu\n", vertex_num, edge_num);
	node_num += 1;
/*
	string sfilename(filename);
	string ligra_name = sfilename.replace(sfilename.rfind(".txt"), 4, "_ligra.txt");
	// string ligra_name = sfilename.substr(0, sfilename.find_first_of('.')) + "_ligra.txt";
	// printf("%s\n%s\n%s\n", sfilename.substr(0, sfilename.find_first_of('.')).c_str(), ligra_name.c_str(), filename);
	
	FILE *fp = fopen(ligra_name.c_str(), "w");
	fprintf(fp, "AdjacencyGraph\n%u\n%llu\n", vertex_num, edge_num);
	uint32 off = 0;
	int startid = start_with_0 ? 0 : 1;
	for (int i = startid; i < graph_csr.size(); i++) {
		fprintf(fp, "%u\n", off);
		off += graph_csr[i].size();
	}
	for (int i = startid; i < graph_csr.size(); i++) {
		auto &ngh = graph_csr[i];
		for (auto &e : ngh) {
			fprintf(fp, "%u\n", e);
		}
	}
	fclose(fp);
*/
}


static int generateCSX(uint32 *data, uint32 *indices, uint64 *info, vector<vector<int>> &graph)
{
	ftime(&time1);
	int count_data = 0;
	int count_max_edge = 0;
	for (int i = 0; i < graph.size(); i++) {
		auto &b = graph[i];
		indices[i] = count_data;
		count_data += b.size();
		if (count_data % ali_num != 0) {
			count_data += (ali_num - count_data % ali_num);
		}
		if (b.size() >= count_max_edge) {
			count_max_edge = b.size();
		}
	}
	indices[node_num] = count_data;
	ftime(&time2);
	measure(time1, time2, sec, msec);
	printf("    %-24s %llu s %d ms\n", "calculate node index", sec, msec);

	#pragma omp parallel for
	for (int i = 0; i < graph.size(); i++) {
		vector<int> &b = graph[i];
		int j = indices[i];
		for (auto elem : b) {
			data[j++] = elem;
		}
		int end = indices[i + 1];
		for (; j < end; j++) {
			data[j] = 0xffffffff;
		}
	}
	ftime(&time1);
	measure(time2, time1, sec, msec);
	printf("    %-24s %llu s %d ms\n", "fill node data", sec, msec);

	// converting graphIndices to graphInfo
	// #pragma omp parallel for
	// for (int k = 0; k < node_num; k++) {
	// 	// graphInfo = neigh_start_index (32 bit) | neighbours_count (32 bit) 
	// 	uint32 index = (uint32)(indices[k] / ali_num);	//	graphIndices/2
	// 	uint32 size = (uint32)((indices[k + 1] - indices[k]) / ali_num);
	// 	info[k] = ((uint64)index << 32) | size;
	// }

	return count_max_edge;
}


static void generateCSC(void)
{
	cscData = (uint32 *) malloc(16 * edge_num * sizeof(uint32));
	cscIndices = (uint32 *) malloc((node_num + 1) * sizeof(uint32));
	// cscInfo = (uint64 *) malloc((node_num + 1) * sizeof(uint64));

	max_edge_csc = generateCSX(cscData, cscIndices, cscInfo, graph_csc);
	graph_csc.clear();
	vector<vector<int>>().swap(graph_csc);	// free
}


static void generateCSR(void)
{
	csrData = (uint32 *) malloc(16 * edge_num * sizeof(uint32));
	csrIndices = (uint32 *) malloc((node_num + 1) * sizeof(uint32));
	// csrInfo = (uint64 *) malloc((node_num + 1) * sizeof(uint64));

	max_edge_csr = generateCSX(csrData, csrIndices, csrInfo, graph_csr);
	graph_csr.clear();
	vector<vector<int>>().swap(graph_csr);	// free
}


static void divideGraph(string &filename)
{
	ftime(&time1);

	vector<vector<uint32>> csc_c(divide_num);
	vector<vector<uint64>> csc_r(divide_num);
	vector<vector<uint32>> csr_c(divide_num);
	vector<vector<uint64>> csr_r(divide_num);
	#pragma omp parallel for
	for (int i = 0; i < divide_num; i++) {
		uint32 csr_index = 0;
		uint32 csc_index = 0;
		vector<uint32> &csr_c_i = csr_c[i];
		vector<uint64> &csr_r_i = csr_r[i];
		vector<uint32> &csc_c_i = csc_c[i];
		vector<uint64> &csc_r_i = csc_r[i];
		for (int r = i; r < node_num; r += divide_num) {
			uint32 idx, size;
			idx = csrIndices[r];			// this is aligned to ali_num
			size = csrIndices[r + 1] - idx;	// so is this
			for (int j = 0; j < size; j++) {
				csr_c_i.push_back(csrData[idx + j]);
			}
			size /= ali_num;
			csr_r_i.push_back(((uint64)csr_index << 32) | size);
			csr_index += size;

			idx = cscIndices[r];
			size = cscIndices[r + 1] - idx;
			for (int j = 0; j < size; j++) {
				csc_c_i.push_back(cscData[idx + j]);
			}
			size /= ali_num;
			csc_r_i.push_back(((uint64)csc_index << 32) | size);
			csc_index += size;
		}
	}
	free(csrData);
	free(csrIndices);
	// free(csrInfo);
	free(cscData);
	free(cscIndices);
	// free(cscInfo);
	ftime(&time2);
	measure(time1, time2, sec, msec);
	printf("    %-24s %llu s %d ms\n", "divide graph", sec, msec);

	unsigned maxcc = 0;
	unsigned maxrc = 0;
	unsigned maxcr = 0;
	unsigned maxrr = 0;
	for (int i = 0; i < divide_num; i++){
		if (csc_c[i].size() > maxcc) {
			maxcc = csc_c[i].size();
		}
		if (csr_c[i].size() > maxrc) {
			maxrc = csr_c[i].size();
		}
		if (csr_r[i].size() > maxrr) {
			maxrr = csr_r[i].size();
		}
		if (csc_r[i].size() > maxcr) {
			maxcr = csc_r[i].size();
		}
	}
	level_addr = maxrr + maxrc / ali_num + maxcr + maxcc / ali_num;
	csc_c_addr = maxrr + maxrc / ali_num + maxcr;
	csc_r_addr = maxrr + maxrc / ali_num;
	csr_c_addr = maxrr;
	csr_r_addr = 0;

	string base = filename + "_pe_" + to_string(pe_num) + "_ch_" + to_string(ch_num) + "_";
	uint64 zero_num = 0;
	int fill_cnt = ali_num / 2 - 1;
	if (fill_cnt < 0)
		fill_cnt = 0;
	#pragma omp parallel for
	for (int i = 0; i < divide_num; i++) {
		string bin = base + to_string(i) + ".bin";
		FILE *fp = fopen(bin.c_str(), "wb+");
		setvbuf(fp, NULL, _IOFBF, 1024 * 128);
		int j;
		vector<uint64> &csr_r_i = csr_r[i];
		// uint64 zerobuf[256 / 64];
		// memset(zerobuf, 0, sizeof(zerobuf));
		for (j = 0; j < csr_r_i.size(); j++) {
			fwrite(&csr_r_i[j], sizeof(uint64), 1, fp);
			// fwrite(zerobuf, sizeof(uint64), 256 / 64 - 1, fp);
			for (int c = fill_cnt; c > 0; c--) {
				fwrite(&zero_num, sizeof(uint64), 1, fp);
				// fwrite(zerobuf, sizeof(zerobuf), 1, fp);
			}
		}
		for (; j < maxrr; j++) {
			for (int c = fill_cnt + 1; c > 0; c--) {
				fwrite(&zero_num, sizeof(uint64), 1, fp);
				// fwrite(zerobuf, sizeof(zerobuf), 1, fp);
			}
		}
		vector<uint32> &csr_c_i = csr_c[i];
		for (j = 0; j < csr_c_i.size(); j++) {
			fwrite(&csr_c_i[j], sizeof(uint32), 1, fp);
			// fwrite(zerobuf, sizeof(uint32), 256 / 32 - 1, fp);
		}
		for (; j < maxrc; j++) {
			fwrite(&zero_num, sizeof(uint32), 1, fp);
			// fwrite(zerobuf, sizeof(uint32), 256 / 32, fp);
		}

		vector<uint64> &csc_r_i = csc_r[i];
		for (j = 0; j < csc_r_i.size(); j++) {
			fwrite(&csc_r_i[j], sizeof(uint64), 1, fp);
			// fwrite(zerobuf, sizeof(uint64), 256 / 64 - 1, fp);
			for (int c = fill_cnt; c > 0; c--) {
				fwrite(&zero_num, sizeof(uint64), 1, fp);
				// fwrite(zerobuf, sizeof(zerobuf), 1, fp);
			}
		}
		for (; j < maxcr; j++) {
			for (int c = fill_cnt + 1; c > 0; c--) {
				fwrite(&zero_num, sizeof(uint64), 1, fp);
				// fwrite(zerobuf, sizeof(zerobuf), 1, fp);
			}
		}
		vector<uint32> &csc_c_i = csc_c[i];
		for (j = 0; j < csc_c_i.size(); j++) {
			fwrite(&csc_c_i[j], sizeof(uint32), 1, fp);
			// fwrite(zerobuf, sizeof(uint32), 256 / 32 - 1, fp);
		}
		for (; j < maxcc; j++) {
			fwrite(&zero_num, sizeof(uint32), 1, fp);
			// fwrite(zerobuf, sizeof(zerobuf), 1, fp);
		}

		fclose(fp);
	}

	ftime(&time1);
	measure(time2, time1, sec, msec);
	printf("    %-24s %llu s %d ms\n", "write divided file", sec, msec);
}


// static void divideGraphNoHash(string &filename)
// {
// 	string base = filename + "_pe_" + to_string(pe_num) + "_ch_" + to_string(ch_num) + "_";

// }


#include <queue>
// Do BFS in CPU and return the number of traversed levels
inline uint32 cpuBFS(uint32 *graphData, uint32 *graphIndices, /*uint32 * level_array,uint32 * visited_map,*/uint32 root) {
	unsigned int *level_array	= (unsigned int*) malloc(4*node_num*sizeof(unsigned int));
	unsigned int *visited_map = (unsigned int *)malloc(((4*node_num-1)/32 + 1)*sizeof(unsigned int));
	for(int i = 0 ; i < node_num ; i++){
		level_array[i] = 0;
	}
	for(int i = 0 ; i < (node_num-1)/32 + 1 ; i++){
		visited_map[i] = 0;
	}
	uint32 level = 1;
	int qc_count = 0;
	int qn_count = 0;
	// declare Next/Current queues
	queue <uint32> Current, Next;
	// Add root to next queue and it's level 1 
	Next.push(root);
	level_array[root] = level;

	// Traverse the graph
	while (!Next.empty()) {
		// pop next level into current level
		level ++;
		int i = 0;
		while (!Next.empty()) {
			Current.push(Next.front());
			i++;
			Next.pop();
		}
		qc_count = 0;
		qn_count = 0;
		// Traverse current level
		while (!Current.empty()) {
			uint32 current = Current.front();
			uint32 neigh_count = graphIndices[current + 1] - graphIndices[current];
			uint32 neigh_index = graphIndices[current];

			qc_count++;

			Current.pop();
			for (uint32 k = 0; k < neigh_count; k++,neigh_index++) {
 				// if neighbor is not visited, visit it and push it to next queue
				if ((visited_map[graphData[neigh_index]/32] & (1 << graphData[neigh_index])) == 0) {
					Next.push(graphData[neigh_index]);
					qn_count++;
					level_array[graphData[neigh_index]] = level;
					visited_map[graphData[neigh_index]/32] = visited_map[graphData[neigh_index]/32] | (1 << (graphData[neigh_index] % 32));
				}else{
				}
			}
			
		}

	}
	//kyle : result 打印最后的level和bitmap，作为比对仿真结果的基准
	FILE * result;
	result = fopen("result.txt", "w");
	for(int i = 0;i < node_num;i++){
		fprintf(result,"level[%u]%u\n",i,level_array[i]);
	}
	fclose(result);
	free(level_array);
	free(visited_map);
	return level;
}


int main(int argc, char *argv[])
{
	bool directed = false;
	if (argc < 4) {
		fprintf(stderr, "please use correct argument!\n");
		fprintf(stderr, "Usage: ScalaBFS [filename with suffix] [the number of channels] [the number of PEs]\n");
		fprintf(stderr, "Example: ScalaBFS soc-livejournal.txt 32 64\n");
		exit(1);
	} else if (argc > 4) {
		directed = true;
	}

	string graphFile(argv[1]);
	string graphName(basename(argv[1]));
	string filename = graphName.substr(0, graphName.find_first_of('.'));
	// string binFile = filename + "_csr_csc.bin";
	string logFile = filename + "_csc.txt";
	string debugFile = filename + "_debug.log";
// printf("%s\n", graphName.substr(0, graphName.find_first_of('.')).append("_ligra.txt").c_str());
// return 0;
	// flog = fopen(logFile.c_str(), "w+");
	// if (flog == NULL) {
	// 	fprintf(stderr, "Error: can't create log file!\n");
	// 	exit(1);
	// }	
	// fdebug = fopen(debugFile.c_str(), "w+");
	// if (fdebug == NULL) {
	// 	fprintf(stderr, "Error: can't create debug file!\n");
	// 	exit(1);
	// }

	ch_num = atoi(argv[2]);
	pe_num = atoi(argv[3]);
	ali_num = pe_num / ch_num * 2;
	// ali_num = 1;
	divide_num = ch_num;
	printf("Start setup ...\n");

	struct timeb start_time, end_time;
	ftime(&start_time);
	
	readGraphFile(graphFile.c_str(), directed);
	printf("Graph file loading done ...\n");
	// ftime(&t1);
	// measure(t0, t1, sec, msec);
	// printf("read\t%llu s %d ms\n", sec, msec);

	generateCSR();
	printf("CSR Data generation done ...\n");
	// ftime(&t2);
	// measure(t1, t2, sec, msec);
	// printf("csr\t%llu s %d ms\n", sec, msec);

	generateCSC();
	printf("CSC Data generation done ...\n");
	// ftime(&t1);
	// measure(t2, t1, sec, msec);
	// printf("csc\t%llu s %d ms\n", sec, msec);

	divideGraph(filename);
	printf("Dividation done ...\n");
	// ftime(&t2);
	// measure(t1, t2, sec, msec);
	// printf("divide\t%llu s %d ms\n", sec, msec);

	string addrFile = filename + "_addr_pe_" + to_string(pe_num) + "_ch_" + to_string(ch_num) + ".log";
	FILE *faddr = fopen(addrFile.c_str(), "w+");
	if (faddr == NULL) {
		fprintf(stderr, "Error: can't create faddr file!\n");
		exit(1);
	}
	fprintf(faddr, "    string bfs_filename = \"/space1/graph_data/%s/ch%d/%s_pe_%u_ch_\"\n", filename.c_str(), ch_num, filename.c_str(), pe_num);
	fprintf(faddr, "    cl_uint csr_c_addr = %u;\n", csr_c_addr);
	fprintf(faddr, "    cl_uint csr_r_addr = %u;\n", csr_r_addr);
	fprintf(faddr, "    cl_uint level_addr = %u;\n", level_addr);
	fprintf(faddr, "    cl_uint node_num = %u;\n", vertex_num);
	fprintf(faddr, "    cl_uint csc_c_addr = %u;\n", csc_c_addr);
	fprintf(faddr, "    cl_uint csc_r_addr = %u;\n", csc_r_addr);
	fprintf(faddr, "    cl_uint push_to_pull_level = ;\n");
    fprintf(faddr, "    cl_uint pull_to_push_level = ;\n");
    fprintf(faddr, "    cl_uint root = ;\n");
    fprintf(faddr, "    double result = ;\n");
    fprintf(faddr, "    result = ;\n");
	fprintf(faddr, "max_edge_csr  = %u;\n", max_edge_csr);
	fprintf(faddr, "max_edge_csc = %u;\n", max_edge_csc);
	
	fclose(faddr);
	// fclose(flog);
	// fclose(fdebug);
	printf("successfully generated graph data, address log and cpuBFS's result!\n");

	ftime(&end_time);
	measure(start_time, end_time, sec, msec);
	printf("Cost %llu s %d ms\n", sec, msec);

	return 0;
}
